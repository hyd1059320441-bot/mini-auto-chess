# 青玉棋会 V1.7.1 交付记录

V1.7.1 / versionCode 12，公开 Spatial SDK 6.1.9，包名com.pico.miniautochess。已安装并启动于B3110（PB331XPGL6040019G）。

## 本轮修改

- 真人生命归零的当次结算立即结束本局，停止其余未完成战斗。已完成的真实参战结果只领取一次基础收入、利息和经验；中断对局不制造胜负，镜像不额外结算本体。
- 终局关闭卡池/玩法详情，显示个人名次和再开入口；存活AI显示“未完赛”。真人存活时保留三人镜像和夺冠流程。
- 修正图片中卡池说明行距挤压：26sp字体使用33sp行高，其他放大字号也显式设置行高，保留滚动阅读。
- 拖动棋子持续金色高亮、放大的脉动方框和头顶“拖动中”标记；松手或取消清除状态并恢复材质。现有商店出售区和售价提示继续使用。

## 验证

`./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain` 成功，95项测试、0失败、0错误、0跳过。SDK实验API门禁、摆放源码门禁与补丁格式检查通过；摆放门禁保留两条既有范围提示，见v1.7.1-placement.json。

100局规则模拟：真人淘汰结束78局、真人夺冠22局。56局在真人淘汰时仍有多名AI存活，31场未结束的并行战斗被中断；每局终局再推进9000秒完整快照不变。30局超过第十回合，三人镜像累计112场。逻辑时长333.3–768.0秒，中位504.0秒；规则模拟不作为真机帧率或视觉验收。

目标设备确认V1.7.1/code12处于运行状态，场景加载16个英雄模板；桌面高度均为.265m，累计前移.30m、模型尺寸.85，HUD数值几何和商店/备战席间距通过。启动期间AndroidRuntime和crash缓冲无记录；SDK仍输出SpatialPack坐标转换日志，原始记录完整保留。

本轮未采集截图，文字阅读、拖动外框和手柄体验由用户佩戴确认。未新增收费3D资源。

## 文件

- dist/v1.7.1/jade-chess-v1.7.1-preview.apk
- dist/v1.7.1/jade-chess-v1.7.1-source.zip
- dist/v1.7.1/manifest.json
- docs/verification/v1.7.1-tests.json、v1.7.1-runtime.json、v1.7.1-installed-apk-check.json
- docs/verification/simulation-v1.7.1-100.md / .csv

APK大小59,512,593字节。真机回拉base.apk与构建文件大小及SHA-256一致：

`5c0178c02cb3bd3f56882f4e6f30edb25cfa89aed8ddfccaf4c15977b43d3699`
