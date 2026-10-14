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

- [x] **1.20.1 构建通过** ✅
- [x] **1.21.1 构建通过** ✅
      ```bash
      export JAVA_HOME="D:/Java/jdk-25"
      ./gradlew :1.20.1:build :1.21.1:build --console=plain
      # → versions/1.20.1/build/libs/autofarm-2.4.1+1.20.1.jar
      # → versions/1.21.1/build/libs/autofarm-2.4.1+1.21.1.jar
      ```

## 1.20.1 → 1.21.1 实际踩到的 API 变化（共 12 类，25 处）

这些是照着编译报错逐个查出来的，后面铺开其它版本时会再遇到同类问题，先记下来。

| 变的东西 | 断点 | 新版写法 |
|---|---|---|
| `ItemStack#isEdible()` | 1.20.5 | `stack.has(DataComponents.FOOD)` |
| `Item#getFoodProperties()` | 1.20.5 | `stack.get(DataComponents.FOOD)`（属性挂在**实例**上了） |
| `FoodProperties#getNutrition()` | 1.20.5 | `food.nutrition()`（变成 record 了） |
| `new ResourceLocation(ns, path)` | 1.21 | `ResourceLocation.fromNamespaceAndPath(ns, path)`（构造函数私有化） |
| `VertexConsumer#vertex/color/endVertex` | 1.21 | `addVertex/setColor`，**`endVertex()` 被删** |
| `Tesselator#getBuilder()` + `buffer.begin(...)` | 1.21 | `Tesselator#begin(mode, format)` 一条搞定 |
| `BufferBuilder#end()` | 1.21 | `buildOrThrow()` |
| `Screen#renderBackground(GuiGraphics)` | 1.20.2 | `renderBackground(GuiGraphics, mouseX, mouseY, delta)` |
| `Screen#mouseScrolled(x, y, amount)` | 1.20.2 | 多了横向滚动量：`(x, y, horizontalAmount, amount)` |
| `Options#renderDebug` | 1.21 | `client.getDebugOverlay().showDebugScreen()`（字段转私有） |
| `SystemToast.SystemToastIds.TUTORIAL_HINT` | 1.21 | `SystemToastId.PERIODIC_NOTIFICATION`（改成单数**且删了 TUTORIAL_HINT**） |
| `ConnectScreen#startConnecting(...5 参数)` | 1.21 | 末尾多一个 `TransferState`，普通重连传 `null` |
| `HudRenderCallback#onHudRender(DrawContext, float)` | 1.21 | `(GuiGraphics, DeltaTracker)`（Fabric API 跟着改） |

> 食物那三处（共 6 个调用点）被收进了新文件
> `client/compat/FoodCompat.java` —— 与其在每个调用点写条件编译，不如把差异集中到一处。

## 怎么查「某个版本里某个 API 到底叫什么」（关键方法论）

写条件编译的难点不是改代码，是**知道改成什么**。按可靠性排序，有三条路：

### 1. Mojang 官方映射文件（最快最准，首选）

每个版本构建过一次之后，本机就有它：

```
~/.gradle/caches/fabric-loom/<MC版本>/layered/working_dir/layered_hash_*/mojang/client.txt
```

ProGuard 格式，列出**每个类的全部字段与方法，且带参数签名**。例如查 Toast 的 token：

```bash
F=~/.gradle/caches/fabric-loom/1.21.1/layered/working_dir/layered_hash_2198/mojang/client.txt
grep -A 25 'toasts\.SystemToast\$SystemToastId ->' "$F"   # $ 要写 \$，在单引号里则原样
grep -E "startConnecting" "$F"                            # 顺便就能看到它的完整参数列表
```

这次 12 类变化里的大部分，都是靠它一锤定音的（比如发现 `TUTORIAL_HINT`
在 1.21 被整个删掉、`renderDebug` 搬去了 `DebugScreenOverlay`）。

### 2. Fabric API 自己的源码 jar（查 Fabric API 的签名变化）

```
versions/<MC版本>/build/loom-cache/remapped_working/*-sources.jar
```

里面有 Fabric API 的完整 Java 源码。`HudRenderCallback` 的新签名就是这么查到的。

### 3. 社区迁移指南（先看大方向）

NeoForge 的 [Migration Primer](https://docs.neoforged.net/primer/docs/1.21)
按版本列出所有破坏性改动，适合**开工前**先摸清这个版本大概动了哪些刀。

## 未完成（下一步）

- [ ] 逐段铺开其余 **20 个版本**（1.20.2 ~ 26.3）。建议按这个顺序推进，
      因为它们各自对应一批 API 变化，一次处理一批最省事：
      1. `1.20.2 / 1.20.3 / 1.20.4` —— 主要是 Screen / GUI 那批（已改好，大概率直接过）
      2. `1.20.5 / 1.20.6` —— 数据组件那批（食物已处理，可能还有别的组件）
      3. `1.21.2 ~ 1.21.4`
      4. `1.21.5 / 1.21.6` —— **渲染管线第一次重写**，`CropHighlighter` 要小心
      5. `1.21.7 / 1.21.8` —— GUI 渲染改成 RenderState 那套
      6. `1.21.9 ~ 1.21.11` —— **渲染第二次重写**
      7. `26.1 ~ 26.3` —— 未混淆 + Java 25 + Fabric API 大改名
- [ ] 处理 `versions/` 目录的双重含义（见「已知坑」第 3 条）。
- [ ] 全部版本跑通后：改 CI 的 `chiseledBuild`、更新 README 的支持版本表、
      发一个多版本的 Release。

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
