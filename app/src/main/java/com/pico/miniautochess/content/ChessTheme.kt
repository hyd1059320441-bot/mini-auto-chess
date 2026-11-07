package com.pico.miniautochess.content

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.math.Color4

/** App-owned art tokens from ART_DIRECTION.md; native PicoTheme roles stay intact. */
object ChessTheme {
    val Ink = Color(0xFF11242B) // design-style: fixed-figma-color ART_DIRECTION.md 墨青
    val Slate = Color(0xFF203C43) // design-style: fixed-figma-color ART_DIRECTION.md 深青灰
    val Ivory = Color(0xFFE8DFCC) // design-style: fixed-figma-color ART_DIRECTION.md 温象牙
    val Jade = Color(0xFF5FC7AD) // design-style: fixed-figma-color ART_DIRECTION.md 青玉
    val Brass = Color(0xFFBBA26B) // design-style: fixed-figma-color ART_DIRECTION.md 黄铜
    val Clay = Color(0xFFD98062) // design-style: fixed-figma-color ART_DIRECTION.md 陶红
    val Skin = Color(0xFFD4A37F) // design-style: fixed-figma-color ART_DIRECTION.md 皮肤分区
    val Steel = Color(0xFF718F9B) // design-style: fixed-figma-color ART_DIRECTION.md 冷钢甲胄
    val Forest = Color(0xFF277765) // design-style: fixed-figma-color ART_DIRECTION.md 林地布料
    val Rune = Color(0xFF7460BC) // design-style: fixed-figma-color ART_DIRECTION.md 符文紫
    val Ember = Color(0xFFA64030) // design-style: fixed-figma-color ART_DIRECTION.md 烬火布料
    val Dawn = Color(0xFFAD8544) // design-style: fixed-figma-color ART_DIRECTION.md 曙光甲胄
    val Stone = Color(0xFF59665E) // design-style: fixed-figma-color ART_DIRECTION.md 灰绿石地
    val DeepLeaf = Color(0xFF214E38) // design-style: fixed-figma-color ART_DIRECTION.md 深绿植物
    val Wood = Color(0xFF684B32) // design-style: fixed-figma-color ART_DIRECTION.md 庭院木构
    val StarGold = Color(0xFFFFD36A) // design-style: fixed-figma-color ART_DIRECTION.md 星级金

    fun scene(color: Color) = Color4(color.red, color.green, color.blue, color.alpha)
}
