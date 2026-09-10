package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val GLYPH_TURN_LEFT = listOf(
    "    ***       ",
    "   *****      ",
    "  *******     ",
    " *********    ",
    "    ***       ",
    "    ***       ",
    "    ***       ",
    "    ********  ",
    "         ***  ",
    "         ***  ",
    "         ***  ",
    "              "
)

val GLYPH_TURN_RIGHT = listOf(
    "       ***    ",
    "      *****   ",
    "     *******  ",
    "    ********* ",
    "       ***    ",
    "       ***    ",
    "       ***    ",
    "  ********    ",
    "  ***         ",
    "  ***         ",
    "  ***         ",
    "              "
)

val GLYPH_STRAIGHT = listOf(
    "      ***     ",
    "     *****    ",
    "    *******   ",
    "   *********  ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "      ***     ",
    "              "
)

val GLYPH_IDLE_FACE = listOf(
    "              ",
    "              ",
    "  ***    ***  ",
    " *****  ***** ",
    " *****  ***** ",
    "  ***    ***  ",
    "              ",
    "              ",
    "   *      *   ",
    "    ******    ",
    "              ",
    "              "
)

val GLYPH_MAC = listOf(
    "  ** ** ** ** ",
    "  *** ** ***  ",
    "  ** ** ** ** ",
    "  ** ** ** ** ",
    "  **       ** ",
    "  ** ** ** ** ",
    "  *** ** ***  ",
    "  ** ** ** ** ",
    "              ",
    "              ",
    "              ",
    "              "
)

val GLYPH_ARCO = listOf(
    "      **      ",
    "    ******    ",
    "  **********  ",
    "  **      **  ",
    "  **      **  ",
    "  **********  ",
    "   ********   ",
    "    ******    ",
    "     ****     ",
    "      **      ",
    "              ",
    "              "
)

val GLYPH_PC = listOf(
    "  **********  ",
    "  *        *  ",
    "  *        *  ",
    "  *        *  ",
    "  **********  ",
    "      **      ",
    "    ******    ",
    "              ",
    "              ",
    "              ",
    "              ",
    "              "
)

val GLYPH_CUP = listOf(
    "              ",
    "     ****     ",
    "    *    *    ",
    "   *      *   ",
    "   *      *   ",
    "    *    *    ",
    "     ****     ",
    "              ",
    "              ",
    "    ***       ",
    "     ***      ",
    "              "
)

val GLYPH_ROCKET = listOf(
    "      **      ",
    "     ****     ",
    "    ******    ",
    "    ******    ",
    "   ********   ",
    "   ** ** **   ",
    "   ** ** **   ",
    "  ****  ****  ",
    "  **      **  ",
    "   *      *   ",
    "              ",
    "              "
)


val GLYPH_SUN = listOf(
    "    *  *  *   ",
    "     ****     ",
    "  * ****** *  ",
    "   ********   ",
    "  * ****** *  ",
    "   ********   ",
    "  * ****** *  ",
    "     ****     ",
    "    *  *  *   ",
    "              ",
    "              ",
    "              "
)

val GLYPH_CLOUD = listOf(
    "              ",
    "              ",
    "      ****    ",
    "    ********  ",
    "   ********** ",
    "  ************",
    "  ************",
    "   ********** ",
    "              ",
    "              ",
    "              ",
    "              "
)

val GLYPH_BATTERY_FULL = listOf(
    "              ",
    "              ",
    "  **********  ",
    "  *        ** ",
    "  * ****** ** ",
    "  * ****** ** ",
    "  * ****** ** ",
    "  *        ** ",
    "  **********  ",
    "              ",
    "              ",
    "              "
)


val GLYPH_BATTERY_EMPTY = listOf(
    "              ",
    "              ",
    "  **********  ",
    "  *        ** ",
    "  *        ** ",
    "  *        ** ",
    "  *        ** ",
    "  *        ** ",
    "  **********  ",
    "              ",
    "              ",
    "              "
)

val GLYPH_BATTERY_HALF = listOf(
    "              ",
    "              ",
    "  **********  ",
    "  *        ** ",
    "  * ***    ** ",
    "  * ***    ** ",
    "  * ***    ** ",
    "  *        ** ",
    "  **********  ",
    "              ",
    "              ",
    "              "
)

@Composable
fun GlyphMatrixDirection(direction: String, modifier: Modifier = Modifier) {
    val pattern = remember(direction) {
        when (direction) {
            "TURN_LEFT" -> GLYPH_TURN_LEFT
            "TURN_RIGHT" -> GLYPH_TURN_RIGHT
            "STRAIGHT" -> GLYPH_STRAIGHT
            "IDLE_FACE" -> GLYPH_IDLE_FACE
            "MAC" -> GLYPH_MAC
            "ARCO" -> GLYPH_ARCO
            "PC" -> GLYPH_PC
            "CUP" -> GLYPH_CUP
            "ROCKET" -> GLYPH_ROCKET
            "SUN" -> GLYPH_SUN
            "CLOUD" -> GLYPH_CLOUD
            "BATTERY_FULL" -> GLYPH_BATTERY_FULL
            "BATTERY_HALF" -> GLYPH_BATTERY_HALF
            "BATTERY_EMPTY" -> GLYPH_BATTERY_EMPTY
            else -> GLYPH_STRAIGHT
        }
    }
    
    val activePixels = remember(pattern) {
        val pixels = mutableSetOf<Pair<Int, Int>>()
        pattern.forEachIndexed { y, row ->
            row.forEachIndexed { x, char ->
                if (char != ' ') pixels.add(Pair(x, y))
            }
        }
        pixels
    }

    Canvas(modifier = modifier) {
        val rows = pattern.size
        val columns = pattern.maxOfOrNull { it.length } ?: rows
        
        val padding = 4.dp.toPx()
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        val pixelSize = minOf(cellWidth, cellHeight) - padding

        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val isActive = activePixels.contains(Pair(x, y))
                val color = if (isActive) Color.White else Color(0xFF151515)
                
                val offsetX = x * cellWidth + (cellWidth - pixelSize) / 2
                val offsetY = y * cellHeight + (cellHeight - pixelSize) / 2
                
                drawRoundRect(
                    color = color,
                    topLeft = Offset(offsetX, offsetY),
                    size = Size(pixelSize, pixelSize),
                    cornerRadius = CornerRadius(pixelSize / 4, pixelSize / 4)
                )
            }
        }
    }
}
