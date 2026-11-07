# 青玉棋会 V1.4.0 发布前核对

本次按现状发布试玩版，使用公开 Spatial SDK 6.1.9。用户已确认本页的素材和文案。本页保留创建云端草稿前的审核快照：当时 GitHub 私有仓库已创建，应用 ID、版本 ID、最终源码提交 ID 和云端预览链接均待生成。后续生成的应用 ID 以 `vibelab.yaml` 为准，本页的待生成值和未提交状态仅描述该快照。

## 应用与构建

| 项目 | 当前值与依据 |
| --- | --- |
| 发布目标 | Vibe Lab，提交审核 |
| 区域与语言 | CN；应用实际界面和发布文案均为中文 |
| 应用名称 | 青玉棋会；与当前安装包及运行界面一致 |
| 包名与入口 | `com.pico.miniautochess` / `.platform.LaunchActivity` |
| 版本 | `V1.4.0`，versionCode 6 |
| 工程平台 | 原生 Kotlin PICO Spatial，`android_spatial` |
| SDK | 公开 BOM 6.1.9；保留 Google、Maven Central、Gradle Plugin Portal 和公开 Volcengine 仓库 |
| 已完成构建验证 | 两轮隔离构建成功；68 项单元测试通过；新包在 B3110 启动并进入实际游戏 |
| GitHub 仓库 | `https://github.com/hyd1059320441-bot/mini-auto-chess` |
| 可见性与分支 | private / main；Vibe Lab 构建需要只读 Deploy Key，尚未安装 |
| 提交方式 | 首次 `add` 写入应用 ID 后，将已批准的源码与 `vibelab.yaml` 创建一个普通提交并推送；不提交根发布素材、缓存、本地配置或认证记录 |
| AI 工具与模型 | Trae / GPT-6 |
| 能力声明 | 手柄、手势、眼动；与最终 APK Manifest 对齐 |
| 协作者 | 无，`teammates: []` |
| 验证范围 | 证明可构建、启动和采集实际玩法；没有重复验收全部旧版对局、所有技能或佩戴体验，没有宣称 90 FPS |

最终验证 APK 的 SHA256：

`ded14220c164e141ba76373d0d08d14bae84bfbdd54e9854bfc400b0b6796b1a`

## 正式素材与来源

除图标外，以下素材均来自本次运行 SDK 6.1.9 包的 B3110 真机。用户戴头显操作，助手负责录制、提取、格式转换和少量说明文字。没有使用设计稿、合成游戏场景或旧版 APK 画面。

原始头显录屏为旋转的双眼画面。正式素材使用其中一个眼睛的画面，转正、等比例缩放并加入横版留边；保留了头显视图的边缘畸变。以下来源时间是原始录制中的时间，仅用于核对素材来源。

| 正式文件 | 来源与内容 | 尺寸 / 大小 |
| --- | --- | --- |
| `assets/icon.jpg` | 已获授权从实际应用 PNG 图标转换，保留青玉骑士图案与颜色；与安装包图标核验一致 | 1024×1024，99,987 字节 |
| `assets/landscape.png` | 本次真机录制第 142 秒；棋盘、英雄和经营界面，附少量应用介绍 | 2560×1440，1,432,357 字节 |
| `assets/detail-1.png` | 第 90 秒；招募英雄与商店 | 2560×1440，1,053,020 字节 |
| `assets/detail-2.png` | 第 100 秒；英雄上场与空间布阵 | 2560×1440，1,101,343 字节 |
| `assets/detail-3.png` | 第 121 秒；自动交锋 | 2560×1440，1,119,673 字节 |
| `assets/detail-4.png` | 第 132 秒；回合推进、生命与经营信息 | 2560×1440，1,375,260 字节 |
| `assets/detail-5.png` | 第 147 秒；下一轮经营与人口变化 | 2560×1440，1,364,950 字节 |
| `assets/demo.mp4` | 原始录制 90.0–149.8 秒，连续 59.8 秒，涵盖招募、拖动、战斗和回合推进 | 2560×1440，H.264 / 30 fps，29,704,520 字节 |
| `assets/demo-cover.png` | 从上述最终视频第 36 秒提取，位于已接受的连续片段内 | 2560×1440，1,016,204 字节 |

素材均可解码，图片文件哈希各不相同；五张详情图分别显示不同的实际状态。图片均小于 5 MB，视频小于 100 MB。视频开头直接显示游戏商店与棋盘，没有黑色片头或录制工具水印。CLI 的正式机器校验仍将在确认后由 Release `add` 执行。

原始录制 SHA256：`0255ab69ce1f030410a812a29fd54971b6960df08e7302f8555daa9a1c139fe6`。

正式视频 SHA256：`0d62b5ef59f4a875378d401795100a10e11c0d782d86b85bd9a81b5e50d00205`。正式素材验证后，原始与中间录制已清理。

[观看视频](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/demo.mp4)

[查看图标](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/icon.jpg) · [查看海报](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/landscape.png) · [查看视频封面](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/demo-cover.png)

[详情一：招募](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/detail-1.png) · [详情二：布阵](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/detail-2.png) · [详情三：战斗](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/detail-3.png) · [详情四：回合](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/detail-4.png) · [详情五：再招募](computer:///Users/bytedance/Documents/ai/mini-auto-chess/assets/detail-5.png)

## 完整发布字段

文案依据已确认的 V1.4 玩法整理；发布图标转换、私有仓库及视频制作已分别获得授权。下面列出即将上传的全部应用、版本和构建字段，应用 ID 与源码 SHA 暂不写入。

```yaml
app:
  name: "青玉棋会"
  slogan: "在环绕棋盘上招募英雄、搭配羁绊，赢下四人棋会"
  description: |
    青玉棋会是一款面向 PICO 头显的单人自走棋试玩游戏。你将与三名 AI 棋手同场经营阵容，在环绕式四人棋盘上经历招募、布阵和自动战斗。每轮准备时选择英雄、安排前后排，再观察英雄交锋与技能效果；对局逐步淘汰棋手，最后决出冠军。完整对局通常约十分钟，也可以在准备阶段提前确认开始战斗。

    - 十二名英雄：不同阵营和职业组成多种阵容，三个同名同星英雄自动合成，最高三星。
    - 羁绊搭配：八种羁绊按上场的不同英雄激活，面板显示人数、门槛和效果。
    - 经营决策：每轮基础五金币，胜利奖励先到账，再按余额计算利息；升级人口时可以查看经验进度。
    - 空间布阵：使用手柄射线或眼手交互招募和拖动棋子，在八乘八棋盘上调整站位。
    - 自动战斗：近战绕行、远程攻击与治疗技能配合刀光、箭线和护盾效果，实时显示生命排名与胜负结算。

    打开应用后选择“开始棋会”，通过前下方商店购买英雄并布阵，点击“准备完毕”进入战斗。适合希望在空间棋盘中体验阵容搭配和轻量策略的玩家。当前为 V1.4 试玩版本，后续将继续完善美术、交互和对局体验。
  tags: ["策略解谜"]
  category: "game"
  icon: "assets/icon.jpg"
  landscape: "assets/landscape.png"
  detail_images:
    - "assets/detail-1.png"
    - "assets/detail-2.png"
    - "assets/detail-3.png"
    - "assets/detail-4.png"
    - "assets/detail-5.png"
  videos:
    - file: "assets/demo.mp4"
      cover: "assets/demo-cover.png"
version:
  name: "V1.4.0"
  notes: |
    首次发布青玉棋会试玩版，提供一名玩家与三名 AI 的本地四人棋会、八乘八棋盘及十二名英雄。
    改善底部商店的多行提示，增加经验进度、升级差额、羁绊和实时排名。
    调整经济结算：每轮基础五金，胜利奖励先到账，失败照常按余额领取利息。
    失败按回合基础与敌方存活人口立即扣血，显示分项计算。
    使用金色五角星表现升星，并补充石纹铺地、植被和木构庭院。
build:
  repo_url: "https://github.com/hyd1059320441-bot/mini-auto-chess"
  repo_visibility: "private"
  tech_stack: "android_spatial"
  ai_tool: "Trae"
  ai_model: "GPT-6"
  features:
    - controller_support
    - hand_tracking
    - eye_tracking
  teammates: []
```

## 确认后的动作

确认这些文案和实际素材适用于本次发布后，继续创建初始云端草稿、提交并推送源码、核验只读 Deploy Key，再保存完整草稿。届时提供服务返回的真实预览链接；提交审核前，协议勾选需要由用户在该页面完成。

目前 `draft_saved` 与 `submitted` 均为 false，云端源码检出、构建、审核及上线状态均未验证。
