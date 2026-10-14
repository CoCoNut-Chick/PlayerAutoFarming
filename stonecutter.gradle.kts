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

        // 26.1 起 GUI 绘制被拆成了「抽取 + 绘制」两拍：
        //   GuiGraphics（一个类包办一切）→ GuiGraphicsExtractor（只负责抽取）
        // 好消息是 API 几乎一一对应（fill / enableScissor / blit / fillGradient
        // 的签名都没动），只有文本方法改了名。
        // 所以源码统一写老名字，构建 26.x 时由这三条规则自动换掉 ——
        // 十个界面文件一个都不用改。
        string(current.parsed >= "26.1") {
            replace("GuiGraphics", "GuiGraphicsExtractor")
        }
        string(current.parsed >= "26.1") {
            replace(".drawString(", ".text(")
        }
        string(current.parsed >= "26.1") {
            replace(".drawCenteredString(", ".centeredText(")
        }
    }
}
