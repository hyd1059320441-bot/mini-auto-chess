# 青玉棋会 V1.6.0 试玩交付

2026-11-07，公开 Spatial SDK 6.1.9；package `com.pico.miniautochess`，versionCode 8。最终包已安装并启动于 B3110（PB331XPGL6040019G），APK SHA-256：

`51e847b3ef9495be90952fdb058daf31fe6809dde006761897d1a0509174b85b`

## 本轮实现

- 16英雄有备战席、棋盘待机、行走、普攻、施法、胜利庆祝六种程序动作。普攻/技能使用各自事件时间并在规则 tick 间逐帧过渡，技能优先，获胜方存活英雄庆祝；暂停与离开冻结。十一名程序人物细化四肢和随手臂运动的武器，躯干、眼耳和衣摆稍作圆润处理。
- 每英雄独立技能图案与原创音效：铜盾、叶箭、符灯、温火、旭日、盘根、符弩、火刃、三羽、六瓣花、星座、熔核、晨钟、荆棘、晶体、重锤。普攻共享近战/箭矢/法术三类。群攻与护盾反击按 actionId 去重主技能声音和图案；其他目标保留命中。
- 血条按真实最大生命每100刻度、每500加粗，蓝条每5刻度（满蓝20段），均显示当前/上限。数值和刻度使用缓存字形与纹理，状态牌独立于旋转身体，敌红我绿、护盾白。
- 主棋盘和三个小棋盘改为连续青玉石纹台面，没有常驻格线或交替色。逻辑8×8、布阵落点与交换规则保留；选中显示己方落点，拖动目标放大变金。
- 亭子补正面横梁，椽子贴合梁顶并加屋顶、斜撑；庭院增加座椅与莲花水盆。四个台面保持世界 y=.265m。
- 原创108 BPM、32小节（约71.1秒）轻松木琴/竖琴音乐，低音量循环；右侧“音乐：开/关”，暂停/后台停止播放。短音改为8声 SoundPool，避免沿用 `seekTo(0)` 的重复播放路径；音乐使用 MediaPlayer。短音仅左右声道定位，不宣称SDK三维空间音频。

## 验证

- `:app:assembleDebug`、`:app:testDebugUnitTest` 成功；84项通过，0失败、0错误、0跳过。包含100局正式规则模拟，以及新增动作优先级、六类姿态边界、血蓝刻度、群攻/护盾事件去重测试。报告 `v1.6-tests.json`，构建 `v1.6-build.log`。
- 16个技能音频文件内容全部不同。PCM最高峰值0.294，循环接缝相邻采样差约0.000196，无削波；清单在 `app/src/main/assets/audio/audio_manifest.json`。真机 `SOUND_BANK_READY decoded=20 status=0`，音乐 `MUSIC_PLAY playing=true looping=true`。
- 真机最终进程27150记录 `SCENE_READY generated=5 templates=16`，18项椽子/横梁接触均 gap=0，四台面 `[.265,.265,.265,.265]`；HUD距桌前沿.100m，几何检查通过。
- 安装路径中实际APK哈希与本地最终APK完全一致，见 `v1.6-installed-apk-check.json`。
- 放置源码 gate 通过、0 violation。两个 advisory 已人工核对：其一为 Stage 被关键词启发式识别为 containment，实际没有 bounded volume，owner/epsilon 在现有模型和亭子日志中明确；其二为前景状态详情深度，状态牌是刻意俯仰的平面覆盖层，未用于证明模型body支持关系。方案见 `docs/UX_V1_6.md`。
- 当前启动日志没有应用 FATAL/初始化失败或音频 bank/音乐失败。日志含平台 DMABUFHEAPS、MediaCodec/SpatialPack 级别E信息；音频20段实际解码成功、应用仍运行，不能把它们称为应用崩溃，也不能声称整份日志没有错误行。

## 未完成与佩戴验收

五个现有生成 GLB 没有骨骼或动画片段，本轮只做整体姿态，不具备专业骨骼行走/攻击动画；十一名程序人物支持四肢动作，但仍是开发造型。完整重新设计与生成、蒙皮和专业六类动画等待额度，本轮没有生成任务、充值或积分消费。

本轮在真机验证安装、初始化、资源加载、音乐启动与几何日志。六种动作观感、技能辨识、密集交战血蓝条可读性、手柄/眼手拖动、庭院美术和听感由用户佩戴验收；未截图录屏，未用SDK6.2的无障碍工具替代6.1.9能力。90 FPS未测。本次是本地V1.6试玩包，Vibe Lab上次提交的V1.4没有更新。

## 产物与源码

`dist/v1.6/jade-chess-v1.6-preview.apk`、`jade-chess-v1.6-source.zip`、`manifest.json`、本交付说明。源码主要在 `rules/HeroPresentation.kt`、`rules/Battle.kt`、`content/ChessScene.kt`、`content/CombatEffects.kt`、`content/ChessAudio.kt`、`content/GameController.kt`、`content/HomeStage.kt` 和 `scripts/create_audio.py`。
