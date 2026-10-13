# 多版本改造进度（1.20.1 → 26.3）

> 这份文件是**中途存档**：记录改造做到哪一步、下一步该干什么。
> 全部完成后可以删掉，把结论并进 README / CHANGELOG。

## 目标

一份源码，构建 **1.20.1 往上的全部 Minecraft 正式版**（共 22 个）：

`1.20.2 / .3 / .4 / .5 / .6` · `1.21 ~ 1.21.11` · `26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3`

（2026 年起 Mojang 改成年份编号，`26.1` 就是原来的「1.22」那次更新。）

## 两个必须记住的前提

### 1. 映射体系在 1.21.11 断代

- **1.20.1 ~ 1.21.11**：游戏是混淆的，用映射构建（原来是 Yarn）。
- **26.1 起**：Mojang **不再混淆代码**，Fabric 也停止维护 Yarn 和 Intermediary。

所以**整个工程统一改用 Mojang 官方映射**（`loomx.applyMojangMappings()`）——
这是唯一能一份源码同时吃下新旧两段的办法。靠 `loom-back-compat` 插件自动切换
Loom 变体：老版本 remap，26.x 不 remap。

### 2. Gradle 必须跑在 JDK 25 上

不只是编译目标——**Loom 1.18 自己就要求 JVM 25**（低于 25 会直接报
`Dependency requires at least JVM runtime version 25`）。

本机已装：`D:\Java\jdk-25`（Temurin 25.0.4.1 LTS，从清华镜像下的）。

```bash
export JAVA_HOME="D:/Java/jdk-25"     # 每次跑 Gradle 前都要
```

各版本的实际编译目标由 `build.gradle.kts` 自动分档：

| MC 版本 | 编译目标 |
|---|---|
| 1.20.4 及以前 | Java 17 |
| 1.20.5 ~ 1.21.11 | Java 21 |
| 26.1 起 | Java 25 |

## 已完成

- [x] **合并 split source set**：`src/client/` 并入 `src/main/`（纯客户端 mod，split 无收益，
      还会和多版本子项目冲突）。去掉 `splitEnvironmentSourceSets()`。
- [x] **Yarn → Mojang 映射迁移**：用 Loom 的 `migrateMappings` 转完全部 49 个文件
      （42 文件、约 1600 处改动）。mixin 里的 `method = "..."` 字符串也一并转好了。
- [x] **Stonecutter 多版本骨架**：
      `settings.gradle.kts` + `stonecutter.gradle.kts` + `build.gradle.kts` +
      `stonecutter.properties.toml`，删掉了原来的 `settings.gradle` / `build.gradle`。
- [x] **资源模板化**：`fabric.mod.json` 与 `*.mixins.json` 里的版本相关字段改成占位符，
      由构建时按版本注入。
- [x] **CI 改造**：`.github/workflows/build.yml` 改用 JDK 25 跑 `chiseledBuild`（全版本构建）。
- [x] **工程配置验证通过**：`./gradlew projects` 能正确列出 `:1.20.1` 和 `:1.21.1`。

## 未完成（下一步）

- [ ] **让 1.20.1 编译通过**。最后一次尝试卡住了，原因见下面「已知坑」第 1 条，
      不是代码问题。重跑命令：
      ```bash
      export JAVA_HOME="D:/Java/jdk-25"
      ./gradlew :1.20.1:compileJava --console=plain
      ```
- [ ] **让 1.21.1 编译通过**。已知的必改点：
      - `AutoFarm.java:26` 和 `CropRegistry.java:290` 用了 `new ResourceLocation(ns, path)`，
        **1.21 起构造函数被私有化**，必须改用静态工厂。写法（Stonecutter 条件编译，
        非当前版本的分支要包在块注释里）：
        ```java
        //? if <1.21 {
        /*return new ResourceLocation(MOD_ID, path);
        *///?} else
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        ```
        注意 `ResourceLocation` 在 1.21.11+ 会被 `stonecutter.gradle.kts` 的替换规则
        自动改名成 `Identifier`，所以源码里统一写 `ResourceLocation` 就行。
- [ ] 逐段铺开其余版本，重点在渲染那几处（`render/CropHighlighter.java` 用了
      `WorldRenderContext`，而 Mojang 在 **1.21.5 / 1.21.6 / 1.21.9** 连续三次重写渲染管线）。
- [ ] 处理 `versions/` 目录的双重含义（见「已知坑」第 3 条）。

## 加一个新版本的步骤

1. `stonecutter.properties.toml` 加一段（照着现有的抄，值从
   `https://meta.fabricmc.net/v2/versions/yarn/<版本>` 和
   Fabric API 的 maven 元数据查）；
2. `settings.gradle.kts` 的 `versions(...)` 里加个名字；
3. 跑 `./gradlew :<版本>:build`，按报错补版本条件代码。

## 已知坑

### 1. Gradle 必须「前台 + 免沙箱」跑

沙箱会拒绝 Gradle 写 `~/.gradle/daemon/9.7.1/registry.bin.lock`，
daemon 注册不上就会**静默死锁**（现象：命令挂着不动、缓存目录几分钟零改动、
CPU 也不动）。而且**后台任务拿不到免沙箱权限**，只有前台执行才行。

所以：**不要用后台方式跑 Gradle**；如果卡住了，先 `./gradlew --stop` 清 daemon 再重来。

### 2. 首次配置每个 MC 版本非常慢

Loom 要下载客户端 jar + 映射，再**反编译整个 Minecraft**。1.20.1 + 1.21.1 两个版本
就让 `./gradlew projects` 跑了 6 分钟。之后走缓存会快很多。

### 3. `versions/` 目录现在有两种含义

- `versions/1.0.0/` ~ `versions/2.4.1/` —— mod 自己的**版本归档**（历史产物，已提交）
- `versions/1.20.1/`、`versions/1.21.1/` —— Stonecutter 给每个 MC 版本建的**构建子项目**

目前还没撞车（mod 版本号和 MC 版本号不会重合），但概念混在一起迟早出问题。
建议把 mod 归档改名成 `releases/`（记得同步改 CHANGELOG / README / PUBLISHING 里的引用）。

### 4. 本机网络

- `github.com` 直连不通 → SSH 走 `ssh.github.com:443`（见 `~/.ssh/config`）
- Maven 源（`maven.fabricmc.net`、`meta.fabricmc.net`、清华镜像）**直连正常**
- JDK 25 用清华 Adoptium 镜像下的，Oracle 官方源跑到一半会断
