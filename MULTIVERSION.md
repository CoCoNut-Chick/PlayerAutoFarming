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

---

## 阶段成果（2026-10-14 晚）：15 / 23 个版本构建通过

通过清单：`1.20.1 · 1.20.2 · 1.20.4 · 1.20.5 · 1.20.6 · 1.21 · 1.21.1 · 1.21.2 · 1.21.3 · 1.21.4 · 1.21.5 · 1.21.6 · 1.21.7 · 1.21.8`

### 这一轮新踩到并解决的（按版本断点）

| 断点 | 变化 | 处理 |
|---|---|---|
| 1.20.2 | Options#renderDebug 搬到 DebugScreenOverlay | `client.getDebugOverlay().showDebugScreen()` |
| 1.20.2 | Screen#renderBackground 加坐标；mouseScrolled 加横向量 | 补参数 |
| 1.20.3 | SystemToastIds（复数）→ SystemToastId，且 TUTORIAL_HINT 被删 | 换 PERIODIC_NOTIFICATION |
| 1.20.5 | ConnectScreen#startConnecting 加 TransferState | 传 null |
| 1.21.2 | 一批：ItemNameBlockItem 被并掉；Registry#get → getValue；getMinBuildHeight → getMinY；getToasts → getToastManager；着色器 Supplier → ShaderProgram 对象 | 见各文件注释 |
| 1.21.5 | RenderSystem 的整套状态开关（enableBlend / depthMask / disableDepthTest / enableCull …）被删除 | 改用 `RenderType.debugQuads` |
| 1.21.5 | Inventory#selected 字段私有化 | 新增 `compat/InventoryCompat` |
| 1.21.5 | Level#isDay() 被删 | 按 `getDayTime() % 24000 < 12000` 判断 |

### 两个新的兼容层

- `compat/InventoryCompat.java` —— 快捷栏选中格的跨版本读写（1.21.5 起字段转私有）
- `compat/FoodCompat.java` —— 食物信息（1.20.5 起改数据组件）

思路相同：**把版本差异关进一个文件**，调用点只写一份。

### 一处彻底摆脱版本依赖的改动

`Direction.fromDelta` 在 1.21.4 被移除。BlockInteractor 里原本用它算「该朝方块的哪一面」，
改成手写「取变化最大的轴」—— 所有版本共用同一段逻辑，不需要任何条件编译。

## 1.21.9 ~ 1.21.11：已查明，待处理（两处结构性变化）

1. **GUI 输入事件换成记录类**：
   - `mouseClicked(double,double,int)` → `mouseClicked(MouseButtonEvent, boolean)`
   - `mouseReleased(double,double,int)` → `mouseReleased(MouseButtonEvent)`
   - `mouseDragged(...,int,double,double)` → `mouseDragged(MouseButtonEvent,double,double)`
   - `keyPressed(int,int,int)` → `keyPressed(KeyEvent)`
   涉及 ScrollPanelScreen / ExtrasScreen / StoragePolicyScreen 等。
2. **Fabric API 在 1.21.9 没有 WorldRenderEvents**（1.21.10 才重新引入），
   而 `CropHighlighter` 正是靠它挂上世界渲染流程的。
   这一版要么做降级（不画描边），要么改走底层 hook —— 需要单独设计。

## 仍未开始

- `26.1 ~ 26.3`：未混淆 + Java 25 + Fabric API 大改名（映射体系在 1.21.11 断代）
- `1.20.3`：编译已通过，卡在 Security Center 拒绝读取缓存的 `modmenu-9.0.0.jar`

---

## 阶段成果（2026-10-14 晚）：15 / 23 个版本构建通过

通过清单：`1.20.1 · 1.20.2 · 1.20.4 · 1.20.5 · 1.20.6 · 1.21 · 1.21.1 · 1.21.2 · 1.21.3 · 1.21.4 · 1.21.5 · 1.21.6 · 1.21.7 · 1.21.8`

### 这一轮新踩到并解决的（按版本断点）

| 断点 | 变化 | 处理 |
|---|---|---|
| 1.20.2 | Options#renderDebug 搬到 DebugScreenOverlay | `client.getDebugOverlay().showDebugScreen()` |
| 1.20.2 | Screen#renderBackground 加坐标；mouseScrolled 加横向量 | 补参数 |
| 1.20.3 | SystemToastIds（复数）→ SystemToastId，且 TUTORIAL_HINT 被删 | 换 PERIODIC_NOTIFICATION |
| 1.20.5 | ConnectScreen#startConnecting 加 TransferState | 传 null |
| 1.21.2 | 一批：ItemNameBlockItem 被并掉；Registry#get → getValue；getMinBuildHeight → getMinY；getToasts → getToastManager；着色器 Supplier → ShaderProgram 对象 | 见各文件注释 |
| 1.21.5 | RenderSystem 的整套状态开关（enableBlend / depthMask / disableDepthTest / enableCull …）被删除 | 改用 `RenderType.debugQuads` |
| 1.21.5 | Inventory#selected 字段私有化 | 新增 `compat/InventoryCompat` |
| 1.21.5 | Level#isDay() 被删 | 按 `getDayTime() % 24000 < 12000` 判断 |

### 两个新的兼容层

- `compat/InventoryCompat.java` —— 快捷栏选中格的跨版本读写（1.21.5 起字段转私有）
- `compat/FoodCompat.java` —— 食物信息（1.20.5 起改数据组件）

思路相同：**把版本差异关进一个文件**，调用点只写一份。

### 一处彻底摆脱版本依赖的改动

`Direction.fromDelta` 在 1.21.4 被移除。BlockInteractor 里原本用它算「该朝方块的哪一面」，
改成手写「取变化最大的轴」—— 所有版本共用同一段逻辑，不需要任何条件编译。

## 1.21.9 ~ 1.21.11：已查明，待处理（两处结构性变化）

1. **GUI 输入事件换成记录类**：
   - `mouseClicked(double,double,int)` → `mouseClicked(MouseButtonEvent, boolean)`
   - `mouseReleased(double,double,int)` → `mouseReleased(MouseButtonEvent)`
   - `mouseDragged(...,int,double,double)` → `mouseDragged(MouseButtonEvent,double,double)`
   - `keyPressed(int,int,int)` → `keyPressed(KeyEvent)`
   涉及 ScrollPanelScreen / ExtrasScreen / StoragePolicyScreen 等。
2. **Fabric API 在 1.21.9 没有 WorldRenderEvents**（1.21.10 才重新引入），
   而 `CropHighlighter` 正是靠它挂上世界渲染流程的。
   这一版要么做降级（不画描边），要么改走底层 hook —— 需要单独设计。

## 仍未开始

- `26.1 ~ 26.3`：未混淆 + Java 25 + Fabric API 大改名（映射体系在 1.21.11 断代）
- `1.20.3`：编译已通过，卡在 Security Center 拒绝读取缓存的 `modmenu-9.0.0.jar`


---

## 两种构建变体：完整版 / 合规版

一份源码出两种产物，开关在 `gradle.properties` 的 `autofarm.edition`：

```bash
./gradlew chiseledBuild                                # 完整版（默认）
./gradlew -Pautofarm.edition=compliant chiseledBuild   # 合规版（Modrinth 上架用）
```

| | 完整版 | 合规版 |
|---|---|---|
| mod id | `autofarm` | `farmpilot` |
| 显示名 | PlayerAutoFarming | FarmPilot |
| 版本号 | 2.5.0（跟 `stonecutter.properties.toml`） | 1.0.0（跟 `gradle.properties`） |
| 资源目录 | `assets/autofarm/` | `assets/farmpilot/`（构建时重定位） |
| 配置文件 | `config/autofarm.json` | `config/farmpilot.json` |
| 穿透描边 | 有 | **无** |
| 自动打怪 | 有 | **无** |
| 流向 | GitHub Releases / Gitee / 国内社区 | Modrinth / CurseForge |

### 为什么合规版必须「真删」而不是「关掉」

Modrinth 的《Content Rules》第 3 条把「X-ray 或穿视不透明方块的能力」与
「自动或辅助 PvP 战斗 / 自动瞄准」列为硬红线，而且**判定依据是 jar 里有没有这个功能，
不以「默认关闭」免责**。所以合规版靠三件事把能力从产物里去掉：

1. **源文件整个排除 + 空实现顶上**（`build.gradle.kts` 里 `if (compliant)` 那段）
   - `client/combat/KillAura.java` → 换成 `src/compliant/java` 下的空实现
   - `client/render/ThroughWallPass.java` → 同上
   - 效果：`KillAura.class` 从 6.2 KB 降到 775 B，攻击代码根本不进 jar
2. **`Edition.COMPLIANT` 编译期常量**（`src/main/java` 与 `src/compliant/java` 各一份）
   - `if (!Edition.COMPLIANT) { ...建穿透开关... }` → javac 认定不可达，**从字节码里删掉**
3. **受击反制只保留「停手」**，去掉「转向攻击者」（那是 aim assist）

### 加替补源码目录时的一个坑

`src/compliant/java` **不能**用 `sourceSets.main.java.srcDir(...)` 挂 ——
Stonecutter 会在脚本求值之后再配置一遍源集目录（等于整体重新赋值），
`srcDir` 加的会被覆盖掉。现象很有迷惑性：**排除生效了、替补却没进去**，
满屏「找不到符号 KillAura / Edition」。
正确做法是挂到编译任务上：

```kotlin
tasks.named<JavaCompile>("compileJava") { source(compliantSrc) }
```

注意只能挂 `compileJava`。用 `tasks.withType<JavaCompile>()` 会连 `compileTestJava`
一起加上，Gradle 会以为「有测试源却一个测试都没发现」而让 `test` 任务失败。

## ⚠️ Stonecutter `replacements` 会「反向」执行

`stonecutter.gradle.kts` 里的替换规则，对**不满足条件**的版本会**反过来**应用。
例如这条 26.1+ 的 GUI 改名：

```kotlin
string(current.parsed >= "26.1") { replace(".drawString(", ".text(") }
```

在 1.20.2 ~ 1.21.11 上会执行 `.text(` → `.drawString(`。
于是源码里任何 `.text(` 都会被改写 —— 包括**与 GUI 毫无关系的**记录访问器
`line.text()`，直接编译不过（报「找不到符号 方法 drawString()」）。

**结论：源码里避免出现会被这些模式命中的标识符。** 目前已踩过的：

| 规则 | 反向时误伤 | 处理 |
|---|---|---|
| `.drawString(` ↔ `.text(` | `Line` 记录的 `text()` 访问器 | 改名 `caption()` |
| `.drawCenteredString(` ↔ `.centeredText(` | —— | 暂无 |
| `ResourceLocation` ↔ `Identifier` | 与 `identifier` 变量不冲突（大小写敏感） | 暂无 |

新增替换规则时，务必先 `grep` 一下反向模式在源码里的出现次数。
