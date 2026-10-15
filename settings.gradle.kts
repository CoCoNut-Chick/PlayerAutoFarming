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
        // 目标版本：1.20.1 往上的所有正式版。
        //
        // 注意 2026 年起 Mojang 改用了年份编号，26.1 就是原本会叫「1.22」的那次更新；
        // 1.21.11 是最后一个混淆版本，26.1 起代码不再混淆（映射体系随之换掉，
        // 详见 MULTIVERSION.md）。
        //
        // ⚠️ 这里是【分批注册】的，不是一次列全：
        // 每注册一个版本，Gradle 配置阶段就要为它解析一遍依赖。实测把全部版本
        // 一次性写进去，会让 maven.fabricmc.net 的连接被压垮 ——
        // 报 "Connect to maven.fabricmc.net:443 failed: Connection timed out"，
        // 哪怕你只想构建其中一个版本。
        //
        // 构建单个版本：  ./gradlew :1.21.8:build
        // 构建全部版本：  ./gradlew chiseledBuild
        versions(
            // ── 1.20.x 全线 ✅ ──
            "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
            // ── 1.21.x 全线 ✅（含两次渲染管线重写、1.21.9 的 mixin 方案）──
            "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5",
            "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",

            // ── 未完成：26.x（未混淆 + Java 25 + Fabric API 大改名）──
            // 还没做完，暂时不注册 —— 否则 chiseledBuild 会连它一起构建、
            // 让整个 CI / 发版流程失败。等 26.3 打通了再放开这一行。
            // "26.3",
        )

        // 平时开发/提交时默认激活的版本（IDE 里看到的就是它）
        vcsVersion = "1.20.1"
    }
}

rootProject.name = "playerautofarming"

// ---------------------------------------------------------------------------
// 「把所有注册的版本都构建一遍」的聚合任务
// ---------------------------------------------------------------------------
// ⚠️ Stonecutter 0.9 本身【不提供】这个任务 —— 官方模板里的 chiseledBuild 是
// 模板自己注册的，插件只负责创建各版本的子项目。所以这里补一个。
//
// 没有它的话，CI 里写 `./gradlew chiseledBuild` 会直接报
//   Task 'chiseledBuild' not found in root project 'playerautofarming'
// ——本项目的 GitHub Actions 从 run #8 起一直是这个原因失败的。
//
// 版本列表通过 subprojects 动态取，所以 settings.gradle.kts 里加减版本时
// 这里不用跟着改。
gradle.rootProject {
    tasks.register("chiseledBuild") {
        group = "build"
        description = "构建 settings.gradle.kts 里注册的全部 Minecraft 版本"
        // 这个 action 在根项目创建后才执行，那时所有子项目都已 include 完毕，
        // 直接把它们的 build 任务列出来即可（加减版本这里不用跟着改）。
        dependsOn(subprojects.map { "${it.path}:build" })
    }
}
