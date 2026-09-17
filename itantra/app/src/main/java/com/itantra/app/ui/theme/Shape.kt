package com.itantra.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Standardized shape tokens. Use these for cards, chips, and buttons
 * instead of inline RoundedCornerShape() calls.
 */
object ITantraShapes {
    val card   = RoundedCornerShape(10.dp)
    val chip   = RoundedCornerShape(6.dp)
    val button = RoundedCornerShape(8.dp)
    val dialog = RoundedCornerShape(16.dp)
    val full   = RoundedCornerShape(50)
}
