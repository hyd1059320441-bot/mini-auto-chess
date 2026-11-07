# 青玉棋会

PICO 原生空间自走棋可试玩原型：1 名真人与 3 名本地 AI，12 名英雄、最高人口 7、最多十回合。无登录、联网对局或付费玩法依赖。

## 试玩

在B3110头显打开“青玉棋会”，当前版本为V1.4。金币经验、五张英雄卡和操作集中在前下方；提示独立多行，显示升级差额，装备已移除，棋子用金色五角星标级。四个棋盘表面同高.265m，商店位于桌面前方。

1. 点击“开始棋会”，立即进入备战并生成五张卡牌，可直接招募。
2. 手柄或眼手按住备战席棋子拖动，松手落入己方四排棋格。也可点选棋子再点目标格；已占用格会交换位置。非法落点返回原位。
3. 卫士放前排，射手、法师和辅助放后排。开战时自动从备战席补齐空余人口，保留已有站位；三个同名同星英雄自动合成。
4. 每轮可刷新、买经验、自动布阵，经验栏显示当前经验/本级所需和升级差额。
5. 战斗自动进行，可切换观战。胜利立即+1金；落败按“回合基础 + 敌方存活人口”立即扣血，排名实时更新。全轮结束给参战者基础5金、按胜利到账后余额每10金1利息、2经验（满级停止累积）。19金胜利最终27，落败25；刚被淘汰者也领取当轮收入。
6. “棋手托管”按相同经济规则自动经营；“接回经营”恢复手动控制。
7. 状态区常驻显示你的八种羁绊人数、激活门槛和具体效果；辅助技能与林地羁绊会回血，没有吸血。
8. 暂停或离开应用会冻结对局。返回后点击“继续”。淘汰和终局有常驻提示，终局可“再开一局”。

## 构建

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device PB331XPGL6040019G
pico-cli app launch com.pico.miniautochess --activity .platform.LaunchActivity --device PB331XPGL6040019G
```

上架构建使用 Android SDK 35、JDK 21 与公开 Spatial BOM 6.1.9，仅从公开 Maven 仓库解析依赖。安装命令不接受 `--format`。音效资源由 `python3 scripts/create_audio.py` 可重复生成，均为原创单声道 PCM。

## 实现与证据

- `app/src/main/java/com/pico/miniautochess/rules`：独立规则、英雄数据、战斗、AI；无 SDK 或 Android 依赖。
- `content/GameController.kt`：规则快照与 UI；`ChessScene.kt`：棋盘与 ECS；`CombatEffects.kt`：原生粒子、刀光、箭线、护盾、治疗与范围技能；`ChessAudio.kt`：空间音效。
- `PRODUCT.md`、`ART_DIRECTION.md`：规则与统一象牙/青玉美术规范。
- `docs/verification`：规则测试、100 局模拟、设备运行及源检查。
- `docs/models/STATUS.md`：12 张参考图、5 个正式模型与剩余七个模型的额度状态。
- `PICO_AUTH_LOGIN_FINDING.md`：已给金燕提交的登录体验缺陷及证据边界。

模型美术尚未全部完成。真机射线命中、阅读舒适性、空间听感及 90 FPS 目标需要佩戴实测；规则模拟和模拟器运行不能代替这项验证。

继承分区材质、程序动画、近战绕行和原生战斗特效。商店十二张图片来自实际英雄参考图；环境使用灰绿石纹铺地、深绿树冠、木构庭院和低位种植池。本轮没有提交新的收费3D任务。
