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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Liquid glass": nền kính mờ nhiều lớp + vệt sáng phản chiếu di chuyển theo độ nghiêng của máy
 * + viền highlight. strength: 0..1
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
            val tx = Tilt.x
            val ty = Tilt.y
            // vệt sáng chính chạy theo độ nghiêng
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.12f + 0.24f * s),
                    1f to Color.Transparent,
                    center = Offset(size.width * (0.22f + 0.5f * tx), size.height * (0.0f - 0.25f * ty)),
                    radius = size.maxDimension * 0.85f
                )
            )
            // ánh phản chiếu nhẹ phía đối diện
            drawRect(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.05f + 0.07f * s),
                    1f to Color.Transparent,
                    center = Offset(size.width * (0.8f - 0.4f * tx), size.height * (1.1f + 0.2f * ty)),
                    radius = size.maxDimension * 0.6f
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

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .glass(CircleShape, 0.7f)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

@Composable
fun GlassField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Color.White),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp), 0.4f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(hint, color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
                }
                inner()
            }
        }
    )
}
