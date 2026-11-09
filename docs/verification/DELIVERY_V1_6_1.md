# V1.6.1：场景前移10厘米

按用户“整体往我朝向的方向偏移一点点”的要求，沿应用正前方（-Z）平移0.10m。棋盘、棋子、HUD、状态面板和庭院同属 scenePlacement，整体移动；内部相对位置不变。placementOwner 在 scenePlacement 内仍保持 identity，接触与落点计算沿用原坐标空间。HUD遮挡审计同步计入新的观察者相对距离。

公开Spatial SDK 6.1.9，包名com.pico.miniautochess，版本V1.6.1/code9。本次仅调整摆放，V1.6的动作、音效、卡池和战斗内容继续沿用；此前84项回归与100局模拟结果见DELIVERY_V1_6.md，本次重新构建并检查安装、根节点位移和场景初始化，不重复规则模拟。

产物在dist/v1.6.1，保留dist/v1.6原包。B3110佩戴位置观感由用户确认。
