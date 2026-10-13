// Stonecutter 的行为配置：当前激活哪个版本、以及「版本之间的差异怎么描述」。
//
// 这里定义的 swaps / replacements 会在构建每个版本时自动作用到源码上，
// 所以源码里只需要写一次，不必为每个版本复制粘贴。

plugins {
    id("dev.kikugie.stonecutter")
}

// 打开 IDE / 直接跑 `./gradlew build` 时默认构建的版本。
// 切版本：改这一行，或跑 `./gradlew "Set active project to 1.21.1"`。
stonecutter active "1.20.1"

stonecutter parameters {
    // ---- 把构建时才知道的常量注入源码 ----
    // 源码里写 /*$ mod_version*/ "2.4.1"，构建时会被换成真实版本号
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    // 发布用的依赖声明（fabric.mod.json / modrinth 元数据）
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    // ---- 版本之间的「字符串级」差异 ----
    replacements {
        // Mojang 在 1.21.11 把 ResourceLocation 改名成了 Identifier。
        // 源码统一写 ResourceLocation，构建 1.21.11+ 时自动换掉。
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }

        // 26.1 起游戏不再混淆，classTweaker 的命名空间从 named 换成 official
        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}
