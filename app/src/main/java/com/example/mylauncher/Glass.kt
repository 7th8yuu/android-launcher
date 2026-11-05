@file:OptIn(ExperimentalFoundationApi::class)

package com.example.mylauncher

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * "Liquid glass": nền kính mờ nhiều lớp + vệt sáng phản chiếu + viền highlight.
 * strength: 0..1
 */
fun Modifier.glass(shape: Shape, strength: Float = 0.55f): Modifier {
    val s = strength.coerceIn(0f, 1f)
    return this
        .clip(shape)
        .background(Color.Black.copy(alpha = 0.08f + 0.10f * s))
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.10f + 0.26f * s),
                    Color.White.copy(alpha = 0.04f + 0.12f * s)
                )
            )
        )
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.10f + 0.22f * s),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.18f, 0f),
                    radius = size.maxDimension * 0.85f
                )
            )
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.70f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.30f)
                )
            ),
            shape
        )
}

/** Nhấn có hiệu ứng lò xo (co lại rồi bật về) + nhấn giữ. */
@Composable
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick
        )
}
