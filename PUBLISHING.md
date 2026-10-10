# 把 PlayerAutoFarming 发布到 GitHub：从零开始

面向「还没注册过 GitHub、也没推送过代码」的情况。照着从上往下做就行，
每一步都写了**为什么**和**出错怎么办**。

命令都按 **Windows + Git Bash** 写（你现在用的就是这个）。用 PowerShell / CMD 时
只有引号和路径写法略有差别，会另外标注。

> 🚧 **GitHub 打不开 / 注册页卡住？** 先看 **[附录 A](#附录-agithub-打不开--注册页卡住怎么办)**（不用任何工具的排查顺序），
> 还不行就走 **[附录 B](#附录-b用-gitee码云发布--国内可用的替代方案)** 用 Gitee 发布 ——
> 前面的步骤 90% 通用，只换网址。**注册不是必须现在做**，本地代码已经全部就绪了。

---

## 第 0 步：发布前先检查三件事

### 0.1 替换掉占位符 `YOUR_NAME`

`README.md` 里有几处 `YOUR_NAME`（顶部的 CI 徽章链接），把它换成你的 GitHub 用户名。

> 还没想好用户名也不要紧：先注册（第 2 步），回来再改，改完再推送。

### 0.2 确认仓库里没有不该公开的东西

这个项目已经检查过一遍，结论是安全的，但你自己以后加东西时要留意：

```bash
# 看看 git 到底跟踪了哪些文件（这些才是会上传到 GitHub 的）
git ls-files

# 全文搜一遍常见的密钥特征
grep -rniE "token|secret|password|api[_-]?key|BEGIN .* PRIVATE KEY" \
     --include='*.java' --include='*.json' --include='*.gradle' --include='*.properties' \
     --include='*.md' .
```

`build/`、`run/`、`.gradle/`、`.idea/`、`.vscode/` 都已经被 `.gitignore` 挡住了，不会上传。

> ⚠️ **一旦推到 GitHub，就等于永久公开。** 事后 `git rm` 只能从最新版本删掉，
> 历史提交里还留着 —— 别人照样能翻出来。所以敏感信息必须在**第一次推送之前**清理干净。

### 0.3 想清楚许可证

现在用的是 **CC0-1.0**（见 `LICENSE`）：等于放弃全部著作权，任何人可以随便用、改、闭源再发布，
都不用署名。它的好处是最省事，坏处是别人拿去卖钱你也管不着。

其他常见选择：

| 许可证 | 别人能做什么 | 适合 |
|---|---|---|
| **CC0-1.0**（当前） | 随便用，无需署名 | 完全不在意别人怎么用 |
| **MIT** | 随便用，但要保留版权声明 | Fabric 模组里最常见，推荐 |
| **LGPL-3.0** | 可以引用，但改了这个模组本身必须继续开源 | 不想让别人闭源拿去卖 |
| **All Rights Reserved** | 只是公开源码给人看，谁也不许用 | 其实不算开源 |

想换的话告诉我一声，我把 `LICENSE`、`fabric.mod.json` 里的 `license` 字段和 README 一起改掉。

---

## 第 1 步：注册 GitHub 账号

1. 打开 <https://github.com/signup>
2. 依次填：**邮箱** → **密码** → **用户名**
   - 用户名会出现在你的主页和仓库地址里（`github.com/你的用户名`），
     之后虽然能改，但旧链接会失效，所以一开始就想好。
   - 建议：英文小写 + 数字/连字符，别用中文（有些工具链对非 ASCII 用户名支持不好）。
   - 例：`coconutchick`、`coconut-dev`。
3. 做一次人机验证（拼图），然后去邮箱点验证链接。
4. **建议顺手开两步验证（2FA）**：Settings → Password and authentication →
   Two-factor authentication。GitHub 现在会强制要求一部分账号开启，早开省事。

> 隐私小提示：邮箱会公开在提交记录里。不想暴露的话，注册后用
> `Settings → Emails → Keep my email addresses private`，
> GitHub 会给你一个 `12345678+用户名@users.noreply.github.com` 的替身邮箱，推送时用它。

---

## 第 2 步：在本机设置 Git 身份

Git 每次提交都会记下「谁提交的」，这些信息会随推送公开。当前仓库里已有的 34 个提交
作者都是 `AutoFarm Dev <dev@autofarm.local>`（占位身份）。

```bash
# 设置成你自己的（--global 表示对这台机器上所有仓库生效）
git config --global user.name "你的名字或昵称"
git config --global user.email "你的邮箱"

# 确认
git config --global --list
```

关于**已经存在的 34 个提交**，三种处理方式，选一个：

| 做法 | 命令 | 结果 |
|---|---|---|
| **A. 不管**（推荐，最省事） | —— | 历史里显示 `AutoFarm Dev`，从下一个提交起显示你 |
| **B. 一律改成你** | 见下面 | 需要 `git-filter-repo`，会改写全部提交哈希 |
| **C. 保留署名，邮件换掉** | 同 B，用 `--mail-map` | 保留 `AutoFarm Dev` 这个名字，只把邮箱换成你的 |

方式 B 的命令（**只在确认要改写历史时执行**，改写后哈希全变，别人已经克隆的仓库会冲突）：

```bash
# 需要先装 git-filter-repo（Python 工具）
pip install git-filter-repo
git filter-repo --name-callback 'return b"你的名字"' \
                --email-callback 'return b"你的邮箱"'

# 改写后 git 会解绑远程仓库，需要重新添加（见第 4 步）
```

> 对这个项目来说，**建议选 A**：`AutoFarm Dev` 本身没有暴露任何隐私，
> 而且它正好是「项目作者」的名字，看起来很自然。

---

## 第 3 步：在 GitHub 上建仓库

1. 右上角 **`+`** → **New repository**
2. 填写：
   - **Repository name**：`PlayerAutoFarming`
   - **Description**（可选）：`Client-side Fabric mod for Minecraft 1.20.1 — auto-harvest and replant crops with visual route planning.`
   - **Public**（公开；私有仓库的 Actions 每月有额度限制，也没法被搜索引擎收录）
   - ❌ **不要**勾 "Add a README file"
   - ❌ **不要**勾 "Add .gitignore"
   - ❌ **不要**选 license
3. 点 **Create repository**

> 为什么都不勾？因为你本地已经有这些文件了。GitHub 那边再初始化一次，
> 两边的历史对不上，第一次推送就会报 `rejected — non-fast-forward`，还得处理冲突。

建完会跳到一个「…or push an existing repository from the command line」的页面，
里面那两行命令就是第 4 步要用的。

---

## 第 4 步：关联远程仓库并推送

先看看本地分支叫什么：

```bash
git branch
```

如果显示 `* master`，先改成 `main`（GitHub 现在的默认分支名，两边统一比较省心）：

```bash
git branch -M main
```

### 认证方式二选一

#### 方式 A：HTTPS + Token（新手推荐）

GitHub **不再接受账号密码**，要用 **Personal Access Token（PAT）** 当密码。

1. 网页右上角头像 → **Settings** → 左侧最下 **Developer settings**
   → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**
2. 设置：
   - Name：`my-laptop`（随便）
   - Expiration：90 天（到期重新生成即可）
   - Repository access：`All repositories`（或只选这一个）
   - Permissions → Repository permissions → **Contents: Read and write**（这一条就够了）
3. 生成后**立刻复制**（离开页面就再也看不到）。

```bash
git remote add origin https://github.com/你的用户名/PlayerAutoFarming.git
git push -u origin main
```

第一次推送会弹出登录窗口：
- 用户名：你的 GitHub 用户名
- 密码：**粘贴刚才那个 Token**（不是账号密码）

> 想让 Git 记住 Token（免得每次输）：Windows 上装
> [Git Credential Manager](https://github.com/git-ecosystem/git-credential-manager)，
> 或者在 Git 里执行 `git config --global credential.helper manager`。

#### 方式 B：SSH（配一次，以后都不用管）

```bash
# 1. 生成密钥（一路回车即可，密码短语可以留空）
ssh-keygen -t ed25519 -C "你的邮箱"

# 2. 把公钥内容复制出来
cat ~/.ssh/id_ed25519.pub
```

3. 粘到 GitHub：头像 → **Settings** → **SSH and GPG keys** → **New SSH key** → 粘贴 → 保存。

```bash
# 4. 测一下（看到 "Hi 用户名! You've successfully authenticated" 就成了）
ssh -T git@github.com

# 5. 关联并推送
git remote add origin git@github.com:你的用户名/PlayerAutoFarming.git
git push -u origin main
```

### 推送成功后

刷新仓库页面，应该能看到全部文件和历史。顶部还会出现一个小的 **build ✅** 徽章 ——
那是 `.github/workflows/build.yml` 里的 CI 在自动构建。

> ⚠️ 顺手改一下 CI 的 JDK：`.github/workflows/build.yml` 里写的是 `java-version: '25'`，
> 而项目要求 JDK 17。改成 `'17'`（或 `'21'`）更稳，否则 CI 可能在旧版 Gradle 上翻车。

---

## 第 5 步：发布第一个 Release（给玩家下载的地方）

**源码仓库 ≠ 下载页。** 玩家想要的是能直接丢进 `mods/` 的 jar，这个要放在 Release 里。

先本地构建一次，把 jar 造出来：

```bash
export JAVA_HOME="D:/Java/jdk-21.0.12.1"   # 换成你自己的 JDK 路径
./gradlew build
ls -l build/libs/
# 应该看到 playerautofarming-2.3.2.jar
```

然后：

1. 仓库页面右侧 **Releases** → **Draft a new release**
2. **Choose a tag** → 输入 `v2.3.2` → 点 **Create new tag: v2.3.2 on publish**
3. **Target**：`main`
4. **Release title**：`v2.3.2`
5. **Describe this release**：把 `CHANGELOG.md` 里 `## 2.3.2` 那一段贴进去（Markdown 直接渲染）
6. **Attach binaries**：把 `build/libs/playerautofarming-2.3.2.jar` 拖进去
   （**只传普通 jar**，不要传 `-sources.jar`、也不要传 `build/` 里的中间产物）
7. 点 **Publish release**

之后每次发版都是这套流程：改版本号 → 构建 → 归档 `versions/<版本号>/` → 提交推送 →
新建一个 Release 挂上 jar。

---

## 第 6 步（可选）：发到 Modrinth / CurseForge

GitHub 的 Release 只方便已经知道你项目的人；想被玩家搜到，要上模组平台。

- **[Modrinth](https://modrinth.com/)** —— 最推荐，支持用 GitHub 账号直接登录，
  可以从 GitHub Release 同步版本，界面友好、审核快。
  需要准备：图标（512×512 PNG）、英文描述、分类、支持的 MC 版本（1.20.1）、
  Loader（Fabric）、环境（Client）、许可证。
- **[CurseForge](https://www.curseforge.com/minecraft)** —— 用户量更大，但上传流程更啰嗦，
  且要求授权协议比较严格。

两个平台都要上传**同一个 jar**，不用为它们重新打包。

> 上传前务必确认模组描述里写清楚了「客户端模组 / 可能违反某些服务器规则」，
> 平台对「外挂/作弊类」模组有各自的政策，被举报下架是有可能的。

---

## 第 7 步：发布之后怎么维护

- **Issue**：别人报 bug 的地方。把「游戏版本 / 模组列表 / 复现步骤 / 日志」写进
  模板能省下大量来回问的时间（需要的话我帮你加一个 Issue 模板）。
- **Pull Request**：别人提交的改动。合并前先本地 `git fetch && git checkout` 跑一遍。
- **分支**：小改动直接在 `main` 上做也行；要试大改动就 `git checkout -b feature/xxx`，
  弄好再合并。
- **版本号**：沿用你现在的规范 —— 新功能进次版本（1.1.0），修 bug 进修订号（1.0.x）。
- **发新版的完整动作**（这个项目现在的流程）：
  1. 改 `gradle.properties` 里的 `version`
  2. 追加 `CHANGELOG.md`
  3. `./gradlew build` → 把 jar 复制到 `versions/<版本号>/`
  4. `git add -A && git commit -m "..." && git push`
  5. GitHub 上建对应 tag 的 Release，挂上新 jar

---

## 常见问题

| 现象 | 原因 / 解决 |
|---|---|
| **注册页打不开 / 一直转圈** | 见 **[附录 A](#附录-agithub-打不开--注册页卡住怎么办)**：换网络（手机热点）→ 换时段 → 换 DNS → 关掉广告拦截插件 |
| **GitHub 一直上不去** | 走 **[附录 B](#附录-b用-gitee码云发布--国内可用的替代方案)**，用 Gitee 发布，功能完全够用；以后再加个 remote 同步回 GitHub |
| `remote: Support for password authentication was removed` | 用了账号密码。改用 **Personal Access Token**（第 4 步方式 A） |
| `! [rejected] main -> main (fetch first)` | GitHub 上建仓库时勾了 README/.gitignore，导致远程有本地没有的提交。要么 `git pull --rebase origin main` 再推，要么删掉仓库重建（**别勾那些框**） |
| `error: remote origin already exists` | 已经加过远程了。`git remote set-url origin <新地址>` 改地址，或 `git remote remove origin` 再重加 |
| `Permission denied (publickey)` | SSH 公钥没传到 GitHub，或用了错的密钥。`ssh -T git@github.com` 先自测 |
| 推送很慢 / 卡在 `Resolving deltas` | 仓库里 `versions/` 有 17MB 的历史 jar，国内网络会慢。可以接受，也可以把历史 jar 从仓库移到 Release 里（需要的话我帮你清） |
| `warning: LF will be replaced by CRLF` | 换行符警告，无害，不用管 |
| CI 失败但本地能构建 | 多半是 `.github/workflows/build.yml` 里的 JDK 版本问题（见第 4 步末尾） |
| 想删掉某个已推送的文件 | `git rm --cached 文件` 只在最新提交里删；历史里还在。敏感信息必须改历史（`git filter-repo`）或直接删库重建 |

---

## 一页速查（假设什么都不想细看）

```bash
# 1. 改 README 里的 YOUR_NAME；设好 git 身份
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"

# 2. GitHub 网页：新建空仓库 PlayerAutoFarming（不要勾任何初始化选项）

# 3. 分支统一成 main 并推送
git branch -M main
git remote add origin https://github.com/你的用户名/PlayerAutoFarming.git
git push -u origin main      # 弹窗里密码位置填 Personal Access Token

# 4. 构建 + 发 Release（网页操作，见第 5 步）
export JAVA_HOME="D:/Java/jdk-21.0.12.1"
./gradlew build
```

---

# 附录 A：GitHub 打不开 / 注册页卡住怎么办

**先排除「是不是 GitHub 自己挂了」**：打开 <https://www.githubstatus.com/>，
如果显示 All Systems Operational，那就是你这边到 GitHub 的网络问题。下面按**成本从低到高**排。

## A.1 先判断卡在哪一步

| 你看到的 | 大概率原因 |
|---|---|
| 整个页面转圈、根本进不去 | DNS 解析不到 / 出口线路不通 |
| 页面出来了，但**验证码区域空白**、按钮点不动 | 注册用的 hCaptcha（`hcaptcha.com`）加载不出来 —— **最常见** |
| 能登录但 `git push` 卡住 / 超时 | git 走的是 443 端口，同样受限 |
| 提示 `Your request could not be processed` | 刷新/提交太频繁，触发了风控 |

## A.2 按顺序试（不用任何额外工具）

1. **换网络**：手机开热点，电脑连热点再试。蜂窝网络和家宽的国际出口线路通常不一样，
   **这一招成功率最高**，值得第一个试。
2. **换时段**：晚 20:00–24:00 是最堵的，早上或凌晨试。
3. **换 DNS**（Windows：设置 → 网络 → 适配器选项 → 属性 → IPv4 → 使用下面的 DNS）：
   - 阿里 DNS `223.5.5.5` / `223.6.6.6`
   - 腾讯 DNS `119.29.29.29`
   - 改完在 Git Bash / CMD 里执行 `ipconfig /flushdns`
4. **换浏览器 / 无痕窗口**，并把广告拦截、隐私类插件**全关掉** ——
   它们经常顺手把验证码域名一起拦了（A.1 第二种情况的常见真凶）。
5. **用手机 GitHub App 注册**（应用商店搜 GitHub）。手机端和网页端流程不完全一样，
   有时网页卡住的正是手机端能过的那一步。
6. **别反复刷新**。失败几次就停手等几分钟，越刷越容易被风控拦。

## A.3 如果手边有可用的国际网络（学校 / 单位提供的）

用它把**注册**这一步做完就行 —— 注册是**一次性**的。
之后日常 `git push` 如果也慢，可以换下面的方案 B：先把代码放到国内平台，
GitHub 只作为镜像，等网络方便时再同步。

## A.4 重要：注册不是必须现在做

代码、文档、构建产物在本地**已经全部就绪**了。GitHub 只是"放出去"的渠道之一。
如果一时半会儿上不去，**直接走附录 B 用 Gitee 发布**，功能上完全够用，
以后想同步到 GitHub 也就是加一个远程地址的事（B.6 有命令）。

---

# 附录 B：用 Gitee（码云）发布 —— 国内可用的替代方案

Gitee 是国内最大的代码托管平台，注册和访问都稳定，界面和 GitHub 高度相似，
**本指南前面 90% 的步骤可以原样照搬**，只是把网址从 `github.com` 换成 `gitee.com`。

## B.1 注册

1. 打开 <https://gitee.com/signup>
2. 用**手机号**注册（也可以用邮箱），收短信验证码 —— 不像 GitHub 那样依赖境外验证码服务，
   国内网络下基本秒过。
3. 之后可以再补邮箱、绑定微信等，都不影响使用。

## B.2 建仓库

和 GitHub 一样：右上角 `+` → **新建仓库**
- 仓库名称：`PlayerAutoFarming`
- **开源**（Gitee 里叫「公开」）
- ❌ 同样**不要**勾「使用 Readme 文件初始化这个仓库」（你本地已经有了）

## B.3 推送

```bash
git branch -M main
git remote add origin https://gitee.com/你的用户名/PlayerAutoFarming.git
git push -u origin main
```

第一次推送会让你输 Gitee 的用户名和密码。
Gitee 也支持**私人令牌**（设置 → 私人令牌），若开了两步验证就必须用令牌当密码。

## B.4 发版（对应 GitHub 的 Release）

仓库页面 → **发行版**（Releases）→ **新建发行版**：
- 标签：`v2.3.2`
- 标题：`v2.3.2`
- 说明：贴 `CHANGELOG.md` 里 `## 2.3.2` 那一段
- **上传附件**：`build/libs/playerautofarming-2.3.2.jar`

## B.5 README 要不要改？

基本不用。README 里用的相对链接（`../../releases`、`../../issues`）在 Gitee 上是**同样的路径规则**，
会正确指向 Gitee 自己的页面。只有顶部那两个徽章要处理：

- GitHub Actions 的 build 徽章在 Gitee 上**没有对应物**，建议删掉那一行；
- shields.io 的版本/许可证徽章（`img.shields.io`）在国内偶尔慢，但通常能显示，可以先留着。

> 需要的话跟我说一声，我帮你出一个"Gitee 版 README"。

## B.6 以后想同步到 GitHub

一个本地仓库可以有**多个远程**，两边互不干扰：

```bash
# 加一个叫 github 的远程（origin 还指向 Gitee）
git remote add github https://github.com/你的用户名/PlayerAutoFarming.git

# 平时推 Gitee
git push origin main
# 想同步到 GitHub 时
git push github main
```

（等 GitHub 能正常访问了再执行第二条。）

## B.7 关于内容审核

Gitee 会对公开仓库做内容审核，对**作弊 / 外挂类**项目的态度比 GitHub 严得多。

建议的做法是**如实写清楚**，别想着含糊其辞去绕审核：

- 描述里写明「Minecraft 客户端自动化模组」「单人在允许的服务器上使用」；
- 保留 README 里那段 **⚠️ 使用前必读** 的免责声明；
- 如果被驳回，就换平台（下面几个），不要改小作文去试探。

## B.8 其他国内托管

| 平台 | 说明 |
|---|---|
| **Gitee** | 最主流，推荐首选 |
| **GitCode**（CSDN） | 支持从 GitHub 一键导入，界面偏开发者社区 |
| **GitLink** | 之江实验室，偏学术/开源项目 |
| **阿里云效 Codeup** | 私有仓库免费额度大，但不适合"开源给人看" |

## B.9 模组怎么让人下载到

托管平台只是"源码放哪"，**玩家真正下载 jar 的地方**可以完全独立于它：

- **模组平台**：[Modrinth](https://modrinth.com/)（支持 GitHub/Gitee 账号登录，可从仓库同步版本）、
  CurseForge（审核更严）；
- **国内社区**：苦力怕论坛（klpbbs）、[mcmod.cn](https://www.mcmod.cn/)（百科收录，可以提交词条）；
- **最省事的临时办法**：网盘（蓝奏云等）或 QQ 群文件，先把 jar 发出去，渠道以后慢慢补。

> 无论发到哪个平台，都**保留「可能违反部分服务器规则」这句声明** ——
> 这既是给使用者的提醒，也是你在被投诉时"我已明确告知"的依据。

