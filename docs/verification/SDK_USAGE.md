# 本次使用的 SDK 证据与待验证项

项目 BOM 6.2.8，Knowledge 6.2，Spatial Editor 6.2.7。当前会话缺少 pico-dev-knowledge 的 graph_stats / switch_workspace / query_graph，工具发现返回 0；因此使用插件 reference、本地公开文档与匹配版本 public sources。没有绕过 Editor gateway 写场景；生成资源通过已授权的 SGS 路径供 Kotlin Entity 使用。

公开文档目录：`/Users/bytedance/Library/PICO/sdk/6.2/agent-vault/spatial`。

- `sample-snippets/sample_mesh.md:2524–2575`：System 无参构造，`context.scene.queryEntity(EntityQueryCondition.hasComponent(...))`，deltaTime 单位秒。
- `api-reference/com.pico.spatial.core.ecs.md`：TransformComponent 的 position 单位米、父坐标空间；`scaleVector`，`eulerAngles`。
- `api-reference/com.pico.spatial.core.math.md`：Color4 为 gamma RGBA；EulerAngles 为度，ZXY 顺序。
- `reference/resources-loading.md`（项目 spatial-sdk-guideline）：GLB 支持，Entity.loadSuspend 可主线程调用，初始加载与场景写入在 SpatialView.initial，离开时显式 destroy。
- SDK 6.2.8 `Entity.kt` public：`clone(Entity.CloneOptions(recursive=true, shouldShareMaterialInstance=true)): Entity?`，`getVisualBounds(relativeTo, recursive, enabledOnly)`；不依赖 private 成员。
- SDK 6.2.8 SpatialView public signature：`modifier, update?, attachments?, initial: suspend(content, attachments)->Unit`。
- SDK 6.2.8 `SpatialAccessibilityActionsScope.kt` public：`SpatialViewContent.setAccessibilityActions(entity, semanticId, actions)` 是稳定扩展（无 Experimental 注解）；需 import `com.pico.spatial.ui.foundation.content.setAccessibilityActions`。
- `Resource` public：`toGlobal(): Unit` 让资源独立于实体生命周期，必须配对 `close()`。
- `sample_spatial_audio.md` 与 `api-reference/com.pico.spatial.core.ecs.resource.md`：`AudioResource.load(name, path)`，加载在 IO，随后回主线程配置 `ObjectAudioComponent` 和 `Entity.prepareAudio`；控制器 `play/seekTo/stop/close` 均为公开接口。当前使用四种原创 mono PCM 和四个发声源。
- `sample_spatial_ui.md:1532–1560`：`LocalSpatialContainerStateOwner`、`SpatialContainerStateObserver` 和 `ON_UNFOCUSED / ON_FOCUSED`；PICO 多显示空间下不能以普通 Activity 被覆盖代替 Stage 失去焦点。忽略 ON_UNSIGHTED，转头不应暂停。
- UnlitMaterial.create().setBaseColor(Color4)、setBaseColorTextureParameter、setApplyToneMapping(false)。模型使用规范化的实际贴图，保留烘焙明暗；运行时不依赖实时灯光。

## 已验证

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` 通过。规则 25 项边界测试 + 1 项 100 局模拟。
- 样式 gate 0 错误、0 警告，`docs/verification/design-style.log`。
- Placement source gate PASS；唯一 advisory 是模板归一化的证据组织启发式，场景为 unbounded Stage，未声称全局包围盒 fit。实际模板运行时 min.y=0、height=.2m，五个均 PASS。
- 五个面板 inventory / contract / project-audit 0 错误；首次 runtime-audit 发现全部五个，67 nodes，non-truncated，0 错误。
- 首次点击开始确实进入 COUNTDOWN，但在准备阶段创建 AI 预览时触发缓存 MeshResource 被释放的崩溃。日志保存在 `resource-lifetime-before-fix.log`。修复所有工厂 mesh/material 与复用 collision/physics 资源为 toGlobal，并在场景销毁后 close；最新 APK 已编译安装。
- 资源修复版本完整完成十回合，`FINISHED round=10 time=526.71844`，并再次开始到 PREPARATION / BATTLE；对应 `emulator-match-resource-fix.log`、`emulator-restart.log`。该次开局因 UI 重建超过 2 秒曾触发错误的自动暂停，继续后完成；不能把暂停等待算作正常对局时长。
- 删除“单帧超过 2 秒就暂停”的误判。改为 Stage 容器焦点生命周期冻结对局，初始加载和返回后的第一帧不追赶离开时间。新版本保留手动继续。
- 无变更的菜单、准备场景与暂停场景跳过重复实体写入；战斗插值仍由 ECS 执行。不据此宣称 FPS 提升。
- B3110 上已确认 SCENE_READY、四类音效 AUDIO_READY，五个面板 runtime-audit 5/5、67 nodes、non-truncated、零错误。

## 当前待验证

- 最终版本的音效事件播放与 Stage 焦点回调的设备检查，记录于最终交付报告。
- 初次安装后过早操作可能遇到 TARGET_ROOT_TIMEOUT 或仅有 SpatialView stub；以实际 SCENE_READY 为操作前提。一次对局中的 query 超时随后恢复，不能归因为账号授权。
- 五面板是本次 A11Y 合同范围。3D 棋格/棋子走原生碰撞、Interactable 和手柄点击路径，尚未对所有动态棋子发布完整屏幕阅读器合同；不能宣称全场景无障碍完成。
- 真实手柄射线命中、舒适性、空间听感和帧率需要用户佩戴验证；不能据模拟器或播放返回值宣称 90 FPS 或空间听感已验收。

## 模型

SGS 5 个任务已成功，12 名英雄的参考图全部在 docs/models，应用模型资源在 app/src/main/assets/models。
5 模型原始高度 1m，实际三角面 5608–5868。三视图投影确认全部面朝 +X，运行时 yaw=-90 转向 +Z，再由战斗朝向控制。
铜盾卫原始贴图偏灰，prepare_models.py 将无彩色部分映射象牙/青玉并保留明暗；全部贴图 1024、非金属、粗糙度 .92。
尚缺 7 个正式生成模型，目前为 7 个彼此不同的职业/阵营雕刻代币，以保持十二英雄玩法；不能宣称全部正式美术完成。
最新 APS 批后查询：100 available、0 frozen，2026-11-09T00:00:00+08:00 刷新。不要再次查询余额或重提已成功任务。
用户已授权十二英雄模型制作与统一青玉象牙风格，原生参考图可继续生成；未授权充值或 Vibe Lab 发布。

## V1.1 接口依据

- `sample_interaction.md:412–414, 495–497`：`LengthUnit` 和 `LocalPhysicalLengthConverter` 均在 `com.pico.spatial.ui.platform`；`with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }` 将像素增量转换为米。
- SDK 6.2.8 `SpatialDragGestureDetector.kt`：独立 `pointerInput` 使用 `detectSpatialDragGesture`，start `(location, value)`，end/cancel `(value)`；`dragAmount` 是逐事件像素增量，生命周期回调为零。手柄和眼手由 `interactionKind` 区分，使用同一落格逻辑，不使用单位未经证实的 raw device pose。
- `sample_material_light.md:602–698`：天空球是样例自定义 Entity + 向内球模型 + Unlit 贴图，不是专用 SDK 天空盒类。当前生成 4096 面、半径 18m 的向内 UV 球，加载本地 GLB，使用 2048×1024 全景贴图。
- 五个 GLB 经实际截面量测移除圆形底座，裁切插值 UV；原始文件保留，处理记录见 `docs/models/preparation.json`。
- 十二张商店头像由现有实际生成参考 JPG 转为 320×320 drawable。五个静态生成模型与七个程序英雄的区别仍保留，后者有四肢枢轴摆动，不存在收费生成的骨骼走路动画。
- UI Attachment 的 `getVisualBounds` 在当前运行时为空。改用 Compose `onSizeChanged` 获取实际像素尺寸，在该面板自己的 `LocalPhysicalLengthConverter` 和 density 下转换为米，再用公开 `Entity.convertPositionTo` 将四个面板角点转到 owner 坐标。面板锚点默认中心，侧面板固定尺寸；数值视线检查与佩戴视觉验收分别报告。
- 天空球 GLB 最终打包检查发现 trimesh 把整数 `[1,1,1,1]` 当作 8-bit RGBA，导出乘色/透明因子为 1/255。已改为 `[255,255,255,255]`，生成器与 APK 打包器都对实际 GLB JSON 断言因子 `[1.0,1.0,1.0,1.0]`；六个应用 GLB 的完整白色/不透明乘色因子均已核验。不能仅以加载成功断言背景亮度正确。

## V1.4 增量接口依据

- 同版本`api-reference/com.pico.spatial.core.ecs.resource.md:1750,4701,4919,5003`：`MeshResource.createPlane(width,height)`、`TextureResource.create(Bitmap)`、`UnlitMaterial.setBaseColorTexture`及`UnlitMaterial.create(BlendingMode.TRANSPARENT)`均为公开接口。星标通过Android Canvas五角Path和金色渐变绘成纹理，复用Mesh/Texture/Material；庭院石地是1024石纹Bitmap和公开平面，不新增外部模型任务。
- 新增纹理和材质均`toGlobal`，在owner销毁后与已有共享资源一起`close`。保持天空原材质纹理参数和正确RGBA因子，不新增实时灯光。
- 场景检查两个advisory已审查：Containment属于已有GLB模板归一化，其owner为`template_<id>`，通过实际getVisualBounds与.001m容差检查；应用Stage为unbounded，没有声明全场景fit。Surface detail属于HUD与真实桌面体积之间的遮挡/视线检查，已计算opaqueTable实际包围盒、面板四角、桌面前沿、眼高范围1.25—1.75m与.04m余量，不是用Z偏移替代实体深度设计。两项均不要求新增业务对象。
