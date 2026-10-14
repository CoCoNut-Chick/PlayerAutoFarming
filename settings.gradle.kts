// 多版本工程入口。
//
// 原来的 settings.gradle 只声明了一个 rootProject.name；现在由 Stonecutter
// 为每个 Minecraft 版本创建一个子项目（:1.20.1、:1.21.1 ……），源码只有一份
// （rootProject 的 src/），靠版本条件编译分出各自的 jar。
//
// 相关文档：https://stonecutter.kikugie.dev/

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // 多版本插件本体
    id("dev.kikugie.stonecutter") version "0.9.8"

    // 让同一个工程既能构建「需要 remap 的老版本」，也能构建「26.1+ 不 remap 的版本」。
    // 没有它就得为 26.x 单独开一套构建脚本。
    // https://codeberg.org/KikuGie/loom-back-compat
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // 按需自动下载对应版本的 JDK（26.x 要 Java 25，本机只有 21）
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // 目标版本：1.20.1 往上的所有正式版（共 23 个）。
        //
        // 注意 2026 年起 Mojang 改用了年份编号，26.1 就是原本会叫「1.22」的那次更新；
        // 1.21.11 是最后一个混淆版本，26.1 起代码不再混淆（映射体系随之换掉，
        // 详见 MULTIVERSION.md）。
        //
        // ⚠️ 这里是【分批注册】的，不是一次列全：
        // 每注册一个版本，Gradle 配置阶段就要为它解析一遍依赖。实测把这 23 个
        // 一次性写进去，会让 maven.fabricmc.net 的连接被压垮 ——
        // 报 "Connect to maven.fabricmc.net:443 failed: Connection timed out"，
        // 哪怕你只想构建其中一个版本。
        // 所以按批次放开：这一批通过了，再把下一批的注释解掉。
        //
        // 构建单个版本：  ./gradlew :1.21.8:build
        // 构建全部版本：  ./gradlew chiseledBuild
        versions(
            // ── 已通过 ✅ ──
            "1.20.1",
            "1.21.1",

            // ── 批次 1：GUI / Screen 那批变化 ──
            "1.20.2", "1.20.4",

            "1.20.3",

            // ── 批次 2：数据组件 ──
            "1.20.5", "1.20.6",

            // ── 批次 3 ──
            "1.21", "1.21.2", "1.21.3", "1.21.4",

            // ── 批次 4：渲染管线第一次重写 ──
            "1.21.5", "1.21.6",

            // ── 批次 5 ──
            "1.21.7", "1.21.8",

            // ── 批次 6：渲染第二次重写 ──
            // ⚠️ 1.21.9 ~ 1.21.11 暂缓，两处结构性变化需要单独一轮处理（均已查明）：
            //   1) GUI 输入事件整套换成记录类：
            //        mouseClicked(double,double,int) → mouseClicked(MouseButtonEvent, boolean)
            //        mouseReleased(double,double,int) → mouseReleased(MouseButtonEvent)
            //        mouseDragged(...,int,double,double) → mouseDragged(MouseButtonEvent,double,double)
            //        keyPressed(int,int,int) → keyPressed(KeyEvent)
            //   2) Fabric API 在这一版**没有** WorldRenderEvents（1.21.10 才重新引入），
            //      而描边渲染正是靠它挂上世界渲染流程的 ——
            //      这一版要么写降级（不画描边）、要么改走底层 hook。
            // 1.21.9 的描边改走 mixin（见 LevelRendererMixin）：搭原版的方块轮廓渲染。
            "1.21.9",
            "1.21.10",
            // ⚠️ 1.21.11（1.x 线的收官版）暂缓，又是一批 GUI / 渲染重构：
            //   - AbstractButton 新增抽象方法 renderContents(GuiGraphics,int,int,float)，
            //     原来的 renderWidget 不再能覆盖它 —— 4 个自定义按钮类都受影响
            //   - RenderType 在 CropHighlighter 里找不到（类结构/包变了）
            //   - AbstractSkeleton 被改名或合并
            //   - Camera#getPosition 也不在原处
            // "1.21.11",

            // ── 批次 7：未混淆 + Java 25 ──
            // "26.1", "26.1.1", "26.1.2", "26.2", "26.3",
        )

        // 平时开发/提交时默认激活的版本（IDE 里看到的就是它）
        vcsVersion = "1.20.1"
    }
}

rootProject.name = "playerautofarming"
