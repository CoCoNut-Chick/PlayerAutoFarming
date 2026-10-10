# 把 PlayerAutoFarming 发布到 GitHub：从零开始

面向「还没注册过 GitHub、也没推送过代码」的情况。照着从上往下做就行，
每一步都写了**为什么**和**出错怎么办**。

命令都按 **Windows + Git Bash** 写（你现在用的就是这个）。用 PowerShell / CMD 时
只有引号和路径写法略有差别，会另外标注。

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
