# PlayerAutoFarming

[![build](https://github.com/YOUR_NAME/PlayerAutoFarming/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_NAME/PlayerAutoFarming/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62b47a.svg)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Fabric-0.19.5%2B-dbb69c.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-CC0--1.0-lightgrey.svg)](LICENSE)

Minecraft Java 版 **1.20.1** 的 Fabric **客户端**模组：自动收割 + 补种，带可视控制面板和最短路径规划。

全部逻辑在客户端完成，服务端只当你是一个普通玩家 —— 原版服务器和模组服务器都能用，
服主不需要安装任何东西。（模组的内部 id 仍是 `autofarm`，配置文件为 `config/autofarm.json`。）

## ⚠️ 使用前必读

- 这是**纯客户端**模组，只替你操作自家角色的键盘鼠标：不发自定义包、不改游戏规则、
  不穿墙、不飞。但它仍然**是自动化**。
- **请在你被允许的范围内使用。** 附加功能里的「自动打怪」属于**作弊级功能**，
  「防踢模式」「极效率瞄准」这类选项是用来降低被识别概率的 ——
  **很多服务器明令禁止自动化 / 脚本**。使用前先确认你所在服务器的规则，被封号、被回档、
  被没收物品等后果**由使用者自行承担**。
- 合适的使用场景：单人存档、自己开的服务器、明确允许自动化模组的服务器。

## 安装

1. 给 Minecraft 1.20.1 装 [Fabric Loader](https://fabricmc.net/use/installer/)（0.19.5 或更高）；
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api)（1.20.1 对应版本）放进 `.minecraft/mods/`；
3. 下载本模组的 jar（从 [Releases](../../releases) 页）放进 `.minecraft/mods/`；
4. 可选：装 [Mod Menu](https://modrinth.com/mod/modmenu)，就能在「模组列表 → 配置」里直接打开面板。

需要 Java 17 或更高。

## 快速上手

| 按键 | 作用 |
|---|---|
| `G` | 打开/关闭控制面板 |
| `` ` ``（反引号） | 一键开关「自动收割」 |
| （默认未绑定） | 打开统计面板，可在「选项 → 按键控制」里自己设一个 |

打开面板 → 「收割」页把总开关打开 → 角色就会自己扫描周围的成熟作物、规划路线、走过去收割补种。

## 功能

- **自动收割**：扫描周围成熟作物 → 规划访问路线 → 走过去 → 挖掉 / 摘取 → 补种
- **瞄准模式**（可选）：精准 / 效率 / 极效率 三档 —— 精准转正中心、效率转到视线命中就挖、
  极效率不转头直接挖（切到极效率会弹风险提示）
- **三种收获方式自动判别**：挖（小麦等）、摘（甜浆果、发光浆果，植株保留）、跳过（瓜茎）
- **存放与补给**：物品按能力分七类、逐类设置「不存 / 全存 / 保留 n 组」；
  背包将满自动去补给点存放，缺种子自动去取，没设补给点则满包停机
- **补种回退**：默认种子槽位 → 快捷栏 → 深度搜索背包（找到后换到手上）
- **作物筛选**：全收 / 白名单 / 黑名单 / 只收能补种的
- **空地补种**（可选）：把附近空着的耕地也种上
- **行走**：A* 寻路 + 2-opt 路线优化，可选锁定疾跑、自动进食
- **安全保护**：低血量停机、附近敌对生物停机
- **附加功能**（默认全部关闭）：附近有玩家停机、防踢模式（四档真人化）、拟人化节奏、
  定时随机休息、工具耐久自动换新、失败目标暂时重试、HUD 详细统计、
  危险反应延迟、空闲时环顾四周、仅在指定时段工作、危险撤退、受击反制、环境自救、
  掉线重连、瓜茎识别、自定义方块名单、骨粉催熟、耗材与备用工具自给、音效与桌面通知
- **掉落物拾取**（附加功能，默认关闭）：
  - **范围拾取**：掉落物用**独立的拾取半径**扫描（4~48 格，与作物扫描半径分开），
    范围内够得着的就排进路线**走过去捡**；还能切「仅农作物产物 / 所有掉落物」。
    范围内的掉落物会用**紫色描边**圈出来，一眼看得见范围。
  - **纯拾取模式**：**独立于「自动收割」总开关** —— 只开它，模组照样工作，但只捡掉落物：
    不收割 / 不补种 / 不催熟 / 不挖瓜 / 不打怪 / 不去取耗材。保命动作照做。
    适合「只想把地上的东西收回来」的场合。
  - **掉落物优先**：躺够设定时长（默认 8 秒）还没进包的掉落物会**插队** ——
    中断手上的路线，专门为它跑一趟，捡完再回来接着干活。
  > ⚠ 物品入包完全由服务端判定（原版客户端连碰撞回调都直接 return），客户端不存在「拾取包」，
  > 所以做不到「隔空吸取」。这里的「范围」= 看得更远、走过去捡 ——
  > 这是客户端唯一能做、又不会被服务器当成作弊的方式。
- **工作范围与作息**（附加功能）：圈定工作区域（圆形半径或两点矩形，区域外目标不碰、
  走出区域自动返回）、仅白天 / 仅晴天工作、疲劳收工（连续工作到上限即停、歇够再上工）
- **自动打怪**（附加功能里的高风险分组，默认关闭）：附近生物进入攻击距离时自动出手。
  **必须先打开「自动收割」总开关**，开启时需先过风险确认屏；默认非暴力（转头瞄准 + 随机间隔），
  可选二级开关「暴力模式」，暴力模式下还能调**暴力程度**（轻度 / 中度 / 保命）。
  **攻击间隔**用输入框填写（50–5000 毫秒，低于 300 标红），**攻击距离**可调
  （超过原版 3.0 格需二次确认）。⚠ 属作弊级功能，多人服务器使用可能被封，后果自负
- **界面**：主控制面板 + 存放策略 / 作物筛选 / 高亮样式 / 附加功能 / 统计 五个次级面板；
  **主面板与附加功能面板都按顶部大类 tab 分页**（仿 Mod Menu），滚动条可用鼠标拖动；
  开关「已开启」显示绿色，高风险的「不推荐开启」项关闭时显示红色
- **兼容**：Mod Menu 集成；masa 全家桶自由视角 / 灵魂出窍

## 版本

当前版本 **2.4.0**。版本规范与完整变更记录见 [CHANGELOG.md](CHANGELOG.md)；
各版本构建产物归档在 `versions/<版本号>/`，发布版本见 [Releases](../../releases)。

## 构建

需要 JDK 17 或更高。

```bash
./gradlew build
```

产物在 `build/libs/playerautofarming-<版本号>.jar`。

> Windows + Git Bash：Gradle 有时拿不到 `JAVA_HOME`，可以先
> `export JAVA_HOME="C:/Program Files/Java/jdk-17"` 再构建。

## 反馈与贡献

- 遇到问题请开 [Issue](../../issues)，附上：游戏版本、Fabric Loader / Fabric API 版本、
  装了哪些模组、复现步骤，以及 `logs/latest.log`（或崩溃报告）。
- 欢迎 Pull Request。动大的改动前建议先开个 Issue 说一声，免得白做。

## 关于本项目 / How it was made

**创意、需求与设计来自 [CoCoNutChick](https://github.com/YOUR_NAME)：** 做什么功能、
每个开关的取舍、什么时候该拦一道风险提示、界面长什么样，全部由作者提出并逐项拍板；
每一版都进游戏实测，发现问题再回头改。

**代码实现由 AI 辅助完成：** 具体的技术方案、代码编写、调试与文档，
是在「作者提需求 → AI 实现 → 作者实测反馈 → AI 修正」的循环里推进的。
换句话说，**这是作者的想法 + AI 的手**。

> This project is the author's idea: the concept, feature set and every design decision came from
> CoCoNutChick, who also play-tested each build. The code itself was written with AI assistance,
> iterating through "author requests → AI implements → author tests → AI fixes".

## 许可

本项目以 **CC0-1.0** 发布（见 [LICENSE](LICENSE)）：可以自由使用、修改、再发布，无需署名。
「Minecraft」是 Mojang Studios 的商标，本项目与 Mojang / Microsoft 无隶属关系。

---

## English

**PlayerAutoFarming** is a client-side Fabric mod for **Minecraft Java 1.20.1** that harvests
fully-grown crops and replants them for you, with a visual control panel and A\* route planning.
Everything runs on the client — no server-side installation is required.

Install [Fabric Loader](https://fabricmc.net/use/installer/) and
[Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1, then drop the jar from the
[Releases](../../releases) page into `.minecraft/mods/`. [Mod Menu](https://modrinth.com/mod/modmenu)
is optional but adds a config button in the mod list. Java 17+ required.

Press `G` for the panel and `` ` `` to toggle harvesting.

> **Use responsibly.** This is a client-side automation mod. Some optional features are
> anti-cheat evasion (mob-killing aura, "anti-kick", instant aiming mode) and are **against the
> rules of many servers**. Check your server's rules before using it, and note that you are
> responsible for any consequences.

Licensed under CC0-1.0. "Minecraft" is a trademark of Mojang Studios; this project is not
affiliated with or endorsed by Mojang or Microsoft.

See [How it was made](#关于本项目--how-it-was-made) above: the idea and all design decisions are
the author's; the code was written with AI assistance.
