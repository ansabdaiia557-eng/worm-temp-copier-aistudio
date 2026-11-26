package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

/** Shared state so every glass panel can sample the same backdrop scene at its own position. */
class GlassSceneState {
    var rootOrigin by mutableStateOf(Offset.Zero)
    var rootSize by mutableStateOf(Size.Zero)
}

val LocalGlassScene = compositionLocalOf<GlassSceneState?> { null }

/** The backdrop: deep purple to teal gradient with soft violet and cyan light orbs. */
fun DrawScope.drawGlassScene(w: Float, h: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to GlassPurpleDeep,
            0.5f to GlassIndigo,
            1f to GlassTealDeep,
            endY = h
        ),
        size = Size(w, h)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xCC8F5BFF), Color(0x558F5BFF), Color.Transparent),
            center = Offset(w * 0.85f, h * 0.10f), radius = w * 0.85f
        ),
        radius = w * 0.85f, center = Offset(w * 0.85f, h * 0.10f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xAA22D3FF), Color(0x4422D3FF), Color.Transparent),
            center = Offset(w * 0.05f, h * 0.88f), radius = w * 0.95f
        ),
        radius = w * 0.95f, center = Offset(w * 0.05f, h * 0.88f)
    )
    // Crisp bokeh discs: sharp outside the panels, frosted away behind them.
    drawCircle(Color(0x3322D3FF), radius = w * 0.16f, center = Offset(w * 0.78f, h * 0.30f))
    drawCircle(Color(0x338F7BFF), radius = w * 0.22f, center = Offset(w * 0.12f, h * 0.55f))
    drawCircle(Color(0x2AFFFFFF), radius = w * 0.09f, center = Offset(w * 0.55f, h * 0.68f))
    drawCircle(Color(0x3322D3FF), radius = w * 0.20f, center = Offset(w * 0.90f, h * 0.82f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0x66B06BFF), Color.Transparent),
            center = Offset(w * 0.1f, h * 0.38f), radius = w * 0.5f
        ),
        radius = w * 0.5f, center = Offset(w * 0.1f, h * 0.38f)
    )
}

@Composable
fun GlassScene(content: @Composable () -> Unit) {
    val state = remember { GlassSceneState() }
    CompositionLocalProvider(LocalGlassScene provides state) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    state.rootOrigin = it.positionInWindow()
                    state.rootSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                }
        ) {
            Canvas(Modifier.fillMaxSize()) { drawGlassScene(size.width, size.height) }
            content()
        }
    }
}

/**
 * Frosted glass panel. The shared backdrop scene is re-drawn at the panel's position and blurred
 * (real RenderEffect blur on Android 12+; slightly stronger tint on older versions), then a white
 * tint, a top sheen, a diagonal highlight edge and a soft drop shadow add depth.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tint: Color = Color.White,
    edgeTint: Color = GlassCyan,
    content: @Composable () -> Unit
) {
    val scene = LocalGlassScene.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x66000000),
                spotColor = Color(0xAA05001A)
            )
            .clip(shape)
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        if (scene != null && blurOk) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .blur(22.dp, BlurredEdgeTreatment.Unbounded)
            ) {
                val dx = scene.rootOrigin.x - origin.x
                val dy = scene.rootOrigin.y - origin.y
                translate(left = dx, top = dy) {
                    drawGlassScene(scene.rootSize.width, scene.rootSize.height)
                }
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = if (blurOk) 0.16f else 0.20f),
                            tint.copy(alpha = if (blurOk) 0.06f else 0.10f)
                        )
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.35f to Color.Transparent
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.60f),
                        0.45f to Color.White.copy(alpha = 0.10f),
                        1f to edgeTint.copy(alpha = 0.35f)
                    ),
                    shape = shape
                )
        )
        content()
    }
}
