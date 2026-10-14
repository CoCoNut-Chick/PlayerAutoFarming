// 每个版本的子项目都会跑这份脚本（:1.20.1、:1.21.1 ……），
// 具体差别由 sc.current（当前构建的版本）决定。
//
// 结构照搬官方模板 stonecutter-versioning/stonecutter-template-fabric，
// 只把依赖换成了本项目实际用到的那几个 Fabric API 模块。

plugins {
    // 这个插件会根据当前 MC 版本自动套用正确的 Loom 变体：
    //   - 1.21.11 及以前 → 需要 remap（yarn/intermediary 时代）
    //   - 26.1 及以后     → 游戏已不混淆，不 remap
    // 没有它就得分两套构建脚本。
    id("dev.kikugie.loom-back-compat")
}

// 产物名：playerautofarming-2.4.1+1.20.1.jar —— 一眼看出是给哪个版本构建的
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

// Minecraft 对 Java 的要求分三档（这是游戏本身的要求，不是我们的选择）
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25   // 26.1 起要 Java 25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21 // 1.20.5 起要 Java 21
    else -> JavaVersion.VERSION_17                          // 1.20.4 及以前 Java 17
}

repositories {
    /**
     * 把某个 group 的查找范围**锁死**在指定的 maven 上。
     *
     * 为什么非锁不可：不锁的话 Gradle 会按仓库顺序挨个去问「这个坐标你有没有」，
     * 而仓库列表里排在前面的是 Loom 自动加的 maven.fabricmc.net ——
     * 它在国内走 Cloudflare，偶发连接超时，**一次超时就足以让整个依赖解析失败**：
     *
     *   Could not GET 'https://maven.fabricmc.net/com/terraformersmc/modmenu/12.0.1/....pom'
     *   > Connect to maven.fabricmc.net:443 failed: Connection timed out
     *
     * 明明 Mod Menu 压根不在那个仓库里，却因为「顺便去问了一句」而把构建拖垮。
     * 锁死之后，这些 group 只会去它真正的 maven 找，绕开那条不稳的链路。
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }

    // Mod Menu 发布在 Terraformers 自己的 maven 上
    strictMaven("https://maven.terraformersmc.com/releases/", "TerraformersMC", "com.terraformersmc")

    // Mod Menu 的传递依赖 placeholder-api 来自 Nucleoid
    strictMaven("https://maven.nucleoid.xyz/", "Nucleoid", "eu.pb4")
}

dependencies {
    /**
     * 只拉需要的 Fabric API 模块，而不是整个 Fabric API。
     * 22 个版本 × 完整 API 会白下载很多东西。
     */
    fun fapi(vararg modules: String) {
        for (m in modules) {
            modImplementation(fabricApi.module(m, sc.properties["deps.fabric_api"]))
        }
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    // 统一使用 Mojang 官方映射。
    // 在仍然混淆的老版本上，Loom 会把它当作「另一种映射」处理并正常 remap；
    // 在 26.1+ 上就是源码本来的名字。这是整个工程能一份源码吃 23 个版本的关键。
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

    fapi(
        "fabric-lifecycle-events-v1",   // ClientTickEvents / ClientLifecycleEvents
        // 快捷键模块：26.1 起 Fabric 把它从 key-binding 改名成了 key-mapping
        if (sc.current.parsed >= "26.1") "fabric-key-mapping-api-v1"
        else "fabric-key-binding-api-v1",
        "fabric-networking-api-v1",     // ClientPlayNetworking
        "fabric-rendering-v1",          // WorldRenderEvents / HudRenderCallback
    )

    // Mod Menu：可选依赖。
    //   compileOnly  → 编译期能对着它的接口写代码（AutoFarmModMenu）
    //   localRuntime → 开发环境装上它，方便直接开配置界面
    // 打包出去的 jar 里不带它，玩家没装也照常能用。
    // 先取到局部变量再拼字符串：直接写进字符串模板里，Kotlin 推断不出
    // sc.properties[...] 的泛型类型（它是 <T> operator get）
    val modMenu = sc.properties.get<String>("deps.modmenu")
    modCompileOnly("com.terraformersmc:modmenu:$modMenu")
    modLocalRuntime("com.terraformersmc:modmenu:$modMenu")
}

loom {
    // 让 Loom 知道 fabric.mod.json 在根项目里（子项目的源码是共享根项目的 src/）
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    decompilerOptions.named("vineflower") {
        // 给 lambda 补上名字，写 mixin 时省事
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")   // 各版本共用一个 run 目录
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    // 刻意【不】声明 toolchain。
    //
    // 官方模板里写了 vendor = ADOPTIUM + languageVersion = 17/21/25，
    // 但那是建立在「能联网自动下载 JDK」的前提上的：声明之后 Gradle 会去找
    // 「Temurin 17」这种具体发行版，本机没有，foojay 去下又连不上，
    // 直接报 "Cannot find a Java installation"。
    //
    // 实际上完全不需要：跑 Gradle 的 JDK 25 可以用 `--release 17` 编译出
    // Java 17 的字节码，产物与用 JDK 17 编译逐字节等价。所以复用当前 JDK，
    // 用下面的 options.release 控制目标版本就够了。
}

// 用 --release 而不是靠 JDK 版本本身来卡住目标字节码版本。
// 这样本机只装一个 JDK 25，也能正确产出 17 / 21 / 25 三档产物；
// 顺带还能挡住「不小心用了高版本才有的 API」——编译期就会报错。
tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
}

tasks {
    processResources {
        // 这里刻意把每个值都取成**纯 String**，而不是直接把 sc.properties[...] 塞进 map：
        // configuration cache 要求任务捕获的对象可序列化，而 sc.properties[...] 是
        // 泛型取值，背后可能挂着 Gradle 脚本对象 —— 会直接报
        // "cannot serialize Gradle script object references" 让整个 build 失败。
        val props: Map<String, String> = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "version" to project.property("mod.version") as String,
            // minecraft 依赖用「版本范围」而不是死版本号，这样一个 jar 能覆盖同一代的小版本
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to Regex("\\d+\\.\\d+")
                .find(sc.properties.get<String>("deps.fabric_loader"))!!.value,
            // 这个 jar 至少要哪个 Java —— 26.x 的 jar 在 Java 21 上根本起不来，
            // 写清楚能让玩家一眼看懂报错
            // （JavaVersion.getMajorVersion() 本身就返回 String）
            "javaMajor" to requiredJava.majorVersion,
        )
        inputs.properties(props)

        filesMatching("fabric.mod.json") { expand(props) }
        // mixin 配置里的 compatibilityLevel 也要跟着 Java 版本走（JAVA_17 / 21 / 25）
        filesMatching("*.mixins.json") {
            expand(mapOf("java" to "JAVA_${requiredJava.majorVersion}"))
        }
    }

    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from(rootProject.file("LICENSE")) { rename { "$it-$name" } }
    }

    // 把各版本的产物汇总到根项目的 build/libs/<版本>/，发版时好找
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "构建当前版本并把产物复制到 build/libs/<mod version>/"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
