# 青玉棋会 V1.10.0 上传核对

用户要求将现有 V1.10.0 上传 Vibe Lab。目标是保存云端草稿并取得预览链接，当前源码未加入联机功能。本记录与 V1.4 的发布核对独立。

## 应用与源码

| 项目 | 本次值与证据 |
| --- | --- |
| 应用 | 青玉棋会，`com.pico.miniautochess` |
| Vibe Lab 应用 ID | `7704536495876636691`，本地配置、认证后的远端状态一致 |
| 当前线上版本 | V1.4.0，版本 ID `7704536495876653075` |
| 目标版本 | V1.10.0 / versionCode 15 |
| 发布地区与语言 | CN；应用主要界面和发布文案均为中文 |
| 技术栈 | `android_spatial`，`project detect` 确认 Spatial SDK 6.1.9 |
| 源码仓库 | https://github.com/hyd1059320441-bot/mini-auto-chess |
| 可见性与分支 | 已有私有仓库，`main` |
| 当前远端源码 | `a173d7bf54a9d76af8e18060633b62411c965891`，仍为 V1.4 |
| 新源码提交 | 待用户明确授权普通 Git 提交与推送后生成 |
| 校验来源 | V1.10 源码包 333 个文件与当前源码逐字节相同；隔离目录的全部应用源码、资源及 Gradle 输入与已验证 V1.10 一致 |
| 本地试玩验证 | 已有 121 项测试及 100 + 150 + 400 局规则模拟通过，B3110 已安装启动；详见 `DELIVERY_V1_10.md` |
| 仓库检查 | 隔离发布文件的基础凭证筛查无发现；根发布素材、`.vibelab/` 和 `.pico-env.json` 已忽略，不进入源码 |
| 构建密钥 | 复用既有只读 Deploy Key；指纹 `SHA256:87XMM8b6QgsvIowFtKjgQ5MRFGAkupZtu2Q4xcV8yYQ`，GitHub API 确认 `read_only=true` |
| GitHub 连通 | API 登录和独立 HTTPS Git 读取均通过；新提交尚未推送 |

## 发布文案

正式字段保存在项目根目录 `vibelab.yaml`。应用 ID、名称、标语、标签“策略解谜”、分类 `game`、私有仓库、`android_spatial`、`Trae`、`GPT-6`、队友空列表均沿用。功能标签为手柄、手部追踪和眼动追踪，已与最终 release APK 声明逐项核对。

更新简介中的英雄数量至 16 名，并说明有限共享卡池、人口等级抽卡、魔抗反制、连胜连败奖励、交战时经营备战席、技能与远程普攻特效、终局名次和重开入口。

版本说明：

> 新增连胜、连败金币奖励：二至三场加一金，四场加二金，五场以上加三金，平局清零；真人与 AI 遵循相同规则。  
> 延长技能特效至 1.2–1.8 秒并增加淡出，战斗二倍速下仍保持可读时长；远程普攻新增箭矢、能量球与拖尾。  
> 英雄扩展至十六名，加入有限共享卡池、按人口等级抽卡、魔抗反制和自动布阵评分。  
> 支持交战期间经营商店与整理备战席，上场棋子锁定，相关合成和补位延至下轮。  
> 改善拖动与卖出反馈、英雄状态条和品质边框；玩家淘汰立即结算，新增居中名次弹窗、再来一局与退出。  
> 修复重开复用随机种子，调整 AI 分步经营时机；三人阶段使用镜像对局，长时间战斗自动加速。

简介末尾明确标注：“展示图片和视频沿用早期试玩版实机素材，实际内容以当前版本为准。”简介 581 字符，版本说明 277 字符，均在当前 CLI 的 1000 字符限制内。

## 沿用素材

使用此前已确认的 V1.4 B3110 真机素材原文件，不重新采集。图标来自已授权转换的实际应用图标，其他素材来自已确认的真机录制。详细来源与原始时间见 `PUBLISH_REVIEW_V1_4_0.md`；本次不将其声明为 V1.10 新画面。

| 文件 | 来源或内容 | 本次文件校验 |
| --- | --- | --- |
| `assets/icon.jpg` | 原应用青玉骑士图标 | JPEG，1024×1024，99,987 字节 |
| `assets/landscape.png` | 棋盘、英雄与经营界面 | PNG，2560×1440，1,432,357 字节 |
| `assets/detail-1.png` | 招募英雄与商店 | PNG，2560×1440，1,053,020 字节 |
| `assets/detail-2.png` | 英雄上场与空间布阵 | PNG，2560×1440，1,101,343 字节 |
| `assets/detail-3.png` | 自动交锋 | PNG，2560×1440，1,119,673 字节 |
| `assets/detail-4.png` | 回合、生命与经营信息 | PNG，2560×1440，1,375,260 字节 |
| `assets/detail-5.png` | 下一轮经营与人口变化 | PNG，2560×1440，1,364,950 字节 |
| `assets/demo.mp4` | 招募、拖动、战斗和回合推进 | 2560×1440，59.8 秒，29,704,520 字节 |
| `assets/demo-cover.png` | 最终视频第 36 秒帧 | PNG，2560×1440，1,016,204 字节 |

8 张图片均可解码且哈希各不相同，5 张详情图保留此前已确认的不同实际状态；尺寸与大小通过。视频 SHA256 为 `0d62b5ef59f4a875378d401795100a10e11c0d782d86b85bd9a81b5e50d00205`，与此前已确认的原文件一致。

## 隔离构建与最终包

从全新 Gradle 缓存、空 Maven Local、无凭证环境和无 Node / Spatial Editor 的 PATH 执行 Gradle Wrapper `:app:assembleRelease --no-daemon --refresh-dependencies`，构建退出码 0。使用 JDK 17、Android SDK 35。所有声明的依赖仓库均为公开 HTTPS，没有 Maven Local、外部本机模块或构建时 Editor / Node 任务；公开 Spatial BOM 6.1.9 的匿名 HTTP 读取返回 200。

| 最终 release 包事实 | 已观察结果 |
| --- | --- |
| 构建产物 | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| 大小 | 53,087,608 字节 |
| SHA256 | `a449ccc50cece7fe88594b7430de8100fc9ff01bcb25710b9627162b13756046` |
| APK 检查工具 | `apkanalyzer manifest print` 与 `aapt2 dump badging`，均退出 0 |
| 应用名称 / 包名 | 青玉棋会 / `com.pico.miniautochess` |
| 版本 | V1.10.0 / 15，与发布配置完全一致 |
| Manifest 功能 | `controller=1`、`handtracking=1`、`picovr.software.eye_tracking=true` |
| 功能标签 | `controller_support`、`hand_tracking`、`eye_tracking`，全部与 APK 正项对应 |
| 原生架构 | `arm64-v8a` |
| 自包含资源 | APK 内 6 个 GLB、23 个 WAV；没有依赖 Editor 打包的 bundle |

本次只新增发布构建验证；已有 V1.10 测试与真机启动证据继续适用。未重新截图、录制或自动玩局。该 release 构建未签名，用于验证 Vibe Lab 源码构建输入；用户本机试玩仍使用原有已安装的 preview APK。

本地构建和仓库读取通过不代表云端已构建、审核或上线。当前 V1.10 源码尚未推送，V1.10 云端草稿尚未保存。

## 剩余动作

1. 获得用户对普通 Git 提交和向现有私有仓库 `main` 推送的明确授权。
2. 核对完整远端 SHA 后，执行 Release `add` 校验及 Release `commit` 保存 V1.10 草稿。
3. 返回服务实际生成的预览链接。提交审核另需用户在预览页确认协议。
