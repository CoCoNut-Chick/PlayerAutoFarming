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
        // 目标版本。加一行就多出一个可构建的版本 —— 这是整个工程最省事的地方。
        //
        // 1.20.1 是原有的基线；1.21.1 是 1.21 线的起点。
        // 其余版本在后续步骤里逐段补齐（见 CHANGELOG 的多版本改造记录）。
        versions(
            "1.20.1",
            "1.21.1",
        )

        // 平时开发/提交时默认激活的版本（IDE 里看到的就是它）
        vcsVersion = "1.20.1"
    }
}

rootProject.name = "playerautofarming"
