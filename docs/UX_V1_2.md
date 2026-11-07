# V1.2 真机反馈修订

用户要求：直接 B3110 真机调试，停止使用模拟器；参考云顶之弈的底部经营布局；英雄不能大片发白；增强技能与命中特效；修复开战未补齐人口、近战卡位不攻击；降低棋盘形成斜角俯视；所有英雄初始朝向敌人；远处文字要可读。

## 本轮执行与目标

继续直接开发、构建、安装并在 PB331XPGL6040019G 验证，不重新登录，不提交收费模型任务，不使用模拟器，不提自动 Bug 工单。使用当前 BOM 6.2.8 / Knowledge 6.2、原生 Stage Full、公开 ECS 和 SpatialUI。

### 底部 HUD 和视角

- 移除两块左右经营/商店面板，合并为前下方的一条固定高度 HUD：左侧金币/经验/人口，中间五张带图英雄卡，右侧刷新/经验/准备/暂停；选中英雄与装备操作在 HUD 内切换，不展开遮挡棋盘。
- 保留原生 PicoTheme、Button、Text 和 Compose 语义，不创建 WindowContainer 或新的根容器。
- 主棋盘中心从 y=.82m 下移至约 .48m，z 约 -1.9m。棋盘保持水平、英雄保持直立，玩家自然向下斜角看棋盘；不修改真实头部追踪姿态。8×8 和人口 7 不变。
- HUD 最终适配真机 1280dp 内容宽度上限，使用1280×450dp；候选中心调整为 `(0, .19, -.56)`，pitch -64°。运行时用真实面板自身长度转换器测量四角，再将 HUD 上沿放在棋盘/备战席射线路径之下；不得复用旧版左右外移逻辑。
- 撤掉远处三个小字 AI 摘要面板，将四人生命/人口/回合统一放在较近的上方状态栏，正文至少 28–32sp，主要数值 32–36sp。对照实际角尺寸和真机文字可读性。
- 己方准备、备战席、菜单展示和 AI 预览均默认朝向敌方；战斗在无目标时按 team 确定初始方向，得到目标后转向目标。

### 自动补位

- `GameEngine.beginBattle()` 当前直接锁定已上场名单，没有补位入口；手动 ready 和倒计时开战都需调用相同补位。
- 每位存活玩家只从已有备战英雄中补满缺口，按强度选择并优先卫士前排/远程后排。
- 保留玩家已经摆好的所有英雄站位和装备，不重新整理已有阵容；人口已满或备战为空时无额外操作，不自动花钱或买新英雄。
- 准备按钮在有任意已拥有英雄时可用，即使全部还在备战席。
- 覆盖全空棋盘、有部分棋子、满人口、无备战、手动/超时开战、金币/英雄池守恒。

### 近战不动

- 当前只有直线追逐加队友分离，没有侧向绕行；移动停止在精确射程边缘，浮点误差也可能造成无限微小追逐而不攻击。这两个因素分别复现验证。
- 增加攻击边缘容差和入场距离余量；移动评估侧向可行路径，必要时换可达目标，防止一直顶着队友。
- 用确定性的站位和步长，不能用随机瞬移、无条件扩大射程、忽略碰撞或允许隔着队友攻击来掩盖卡住。
- 对照 1v1 最远角、密集 7v7、边缘堵塞、队友挡路、死亡后重新选目标。真机战斗摘要记录攻击次数、最大阻塞时间和有效技能，不能仅看对局终局即称修复。

### 材质与特效

- 现有七名程序英雄主要由大块象牙纯色组成，确实缺少贴图和表面明暗；五个生成 GLB 保持真实贴图并记录运行时纹理绑定。铜盾卫的原始无彩色贴图目前被主要映射为象牙，需要改为更明确的甲胄配色。
- 增加阵营布料/甲胄纹理和分区颜色，保留脸、护甲、武器、饰品的区分；不得把丢失贴图问题归结为风格选择。
- 使用公开 `ParticleComponent` 和 `UnlitMaterial.create(BlendingMode.ADD)` 实现受控的运行时特效：近战刀光/命中火花、弓弩箭线、符文电光、治疗螺旋/光环、盾罩/反击波、焰刃弧、范围法术冲击环。优先让技能轮廓和颜色一眼可区分。
- 预加载贴图、共享并关闭资源，使用有限实体池和粒子上限，只在当前观战的战斗播放。暂停/切换/终局清理暂态特效；不增加一堆动态灯和阴影。
- 生成器和最终 APK 继续断言天空球的完整白色 RGBA `[1,1,1,1]`，避免重现 trimesh 8-bit 因子为 1/255 的问题。

## 已核验接口/证据

- 项目 `gradle/libs.versions.toml` 和 `pico-cli project detect` 均确认 BOM 6.2.8、Knowledge 6.2、Stage Full；未升级工具链，可复用本 Host 的成功环境检查。
- 真实设备当前 V1.1 日志已经提供购买、手动布阵、ready 和三轮战斗的真实操作证据，当前没有为这些症状自动提单。
- `sample_particle.md:263–560` 使用 `ParticleComponent`、`mainSimulation`、`ParticleBlendingMode.ADDITIVE`、`ParticleFloatVaryingProperty`、`ParticleColorVaryingProperty` 和 `TextureResource`。
- `api-reference/com.pico.spatial.core.ecs.md:3859–4105` 明确允许运行时创建 ParticleComponent；新组件属性设置前不可读取 Loader 默认值；支持 mainBurstCount/mainBurstCycles 等瞬时发射配置。
- `api-reference/com.pico.spatial.core.ecs.resource.md` 提供 UnlitMaterial 的 ADD 混合、setBaseColorTextureParameter 与 MaterialTextureParameter.texture。
- UI Attachment 的 getVisualBounds 为空；继续使用 onSizeChanged + 面板自己的 LocalPhysicalLengthConverter + Entity.convertPositionTo。转换缓存需要跨帧同步后复核。

本文件是实施依据，实际通过项和真机结果由最终交付报告记录，不能把上述目标当作已验证结果。
