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

// ---------------------------------------------------------------------------
// 构建变体：完整版 / 合规版
// ---------------------------------------------------------------------------
// 开关在 gradle.properties 的 autofarm.edition。
//
// 合规版是给模组平台上架用的。Modrinth《Content Rules》第 3 条把
// 「穿透不透明方块（X-ray）」和「自动 / 辅助战斗、自动瞄准」列为硬红线，
// 而且判定依据是「jar 里**有没有**这个功能」，**不以「默认关闭」免责**。
// 所以合规版不能只把开关关掉，必须让这些代码从产物里消失 —— 靠两件事：
//   1. 源文件整个排除，再拿空实现顶上（见下面的 sourceSets 配置）
//   2. Edition.COMPLIANT 编译期常量为 true，UI 入口被 javac 折叠掉
val compliant: Boolean = (findProperty("autofarm.edition") as String? ?: "full")
    .equals("compliant", ignoreCase = true)

/** 产物元数据 —— 两种变体各有各的 id / 显示名 / 版本号 */
val modId: String = if (compliant) {
    property("farmpilot.id") as String
} else {
    sc.properties.get<String>("mod.id")
}
val modName: String = if (compliant) {
    property("farmpilot.name") as String
} else {
    sc.properties.get<String>("mod.name")
}
val modVersion: String = if (compliant) {
    property("farmpilot.version") as String
} else {
    property("mod.version") as String
}

/**
 * 写进 fabric.mod.json 的描述（游戏内 Mod 列表 / Mod Menu 里显示的那句）。
 * 两种变体分开写：合规版的措辞刻意避开「自动化 / 作弊 / 外挂」这类词 ——
 * 平台规则把「以 cheat / hack 名义宣传」也算违规，描述本身就是审核对象。
 */
val modDescription: String = if (compliant) {
    "A client-side farming helper for your own worlds: walks your character to " +
        "fully-grown crops, harvests and replants them, plans the shortest route, " +
        "and shows everything on a visual control panel and HUD."
} else {
    "Drives your character to harvest fully-grown crops and replant the seeds, " +
        "with a visual control panel and shortest-path route planning. Client-side only."
}

// 产物名：autofarm-2.4.1+1.20.1.jar / farmpilot-1.0.0+1.20.1.jar
// —— 一眼看出是哪个变体、给哪个 MC 版本构建的
version = "$modVersion+${sc.current.version}"
base.archivesName = modId

if (compliant) {
    // ── 把被平台禁止的能力从产物里真正拿掉 ──
    // 排除后由 src/compliant/java 下的同名空实现顶上，
    // 所以调用点（FarmTaskManager / CropHighlighter）一行都不用改。
    // 单独放在这个目录还有个好处：它不参与 Stonecutter 的版本预处理，
    // 空实现里不需要任何 //? if 条件。
    val compliantSrc = rootProject.file("src/compliant/java")
    sourceSets.getByName("main").java.apply {
        exclude("**/client/combat/KillAura.java")        // 自动打怪 / 受击还手
        exclude("**/client/render/ThroughWallPass.java") // 穿透描边
        exclude("**/client/Edition.java")                // 换成 COMPLIANT=true 的那份
    }

    // 替补文件直接挂到**编译任务**上，而不是用 srcDir 加进源集。
    // 原因：Stonecutter 会在脚本求值之后再配置一遍源集目录（等于整体重新赋值），
    // 用 srcDir 加的会被覆盖掉 —— 现象很有迷惑性：排除生效了、替补却没进去，
    // 于是满屏「找不到符号 KillAura / Edition」。
    // 任务级 source 与源集目录是两套东西，不受那次覆盖影响。
    tasks {
        processResources {
            // 合规版的资源目录跟着 mod id 走：assets/autofarm/ → assets/farmpilot/。
            // 语言文件放在这个目录下，而目录名就是资源命名空间 —— 不改的话
            // 合规版产物里会留着一个写着 autofarm 的目录，等于把两个项目关联起来。
            if (compliant) {
                eachFile {
                    path = path.replace("assets/autofarm/", "assets/$modId/")
                }
            }
        }
    }

    // 注意只能挂 compileJava —— 用 withType<JavaCompile>() 会连 compileTestJava
    // 一起加上，Gradle 会以为「有测试源却一个测试都没发现」而让 test 任务失败。
    tasks.named<JavaCompile>("compileJava") {
        source(compliantSrc)
    }
}

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
            "id" to modId,
            "name" to modName,
            "version" to modVersion,
            "description" to modDescription,
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
        val name = modId
        inputs.property("mod_id", name)
        from(rootProject.file("LICENSE")) { rename { "$it-$name" } }
    }

    // 把各版本的产物汇总到根项目的 build/libs/<版本>/，发版时好找
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "构建当前版本并把产物复制到 build/libs/<mod version>/"

        inputs.property("version", modVersion)
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/$modVersion"))
    }
}
