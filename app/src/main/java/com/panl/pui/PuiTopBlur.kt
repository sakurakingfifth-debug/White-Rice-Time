package com.panl.pui

/*
 * PUI 首页顶部「渐变模糊过渡」（真正的 progressive blur：越靠屏幕顶端越模糊，向下逐渐变清晰）
 *
 * 原理：对「会滚到顶部下面的那一层内容」（HorizontalPager）整体加一个 AGSL RenderEffect，
 * 模糊半径只取决于像素的 y 坐标，所以不管怎么滚动，顶部那一段始终是模糊的；
 * 再叠一层从 surface 色渐隐的遮罩，让状态栏图标更清楚。
 * 需要 Android 13+（RuntimeShader），本项目 minSdk 36，无需版本判断。
 *
 * 接入（改 PuiUi.kt 的 PuiContent，共 3 处）：
 *
 *   1) 在 HorizontalPager 的 modifier 上加 progressiveTopBlur：
 *        val blurH = rememberPuiTopBlurHeight()          // 状态栏高度 + 64dp
 *        HorizontalPager(
 *            state = pagerState,
 *            modifier = Modifier.fillMaxSize().progressiveTopBlur(blurH),
 *            ...
 *        )
 *
 *   2) 在同一个根 Box 里、Pager 之后、标题之前，放渐隐遮罩：
 *        PuiTopFade(height = blurH)
 *
 *   3) 各页内容（PuiPageSize / PuiPageMore 的第三个 Float 参数那个顶部留白）
 *      起点要大于等于 blurH，这样静止时内容不会落在模糊区里，滚动到顶部才被虚化。
 *
 * 如果原来那条 Modifier.blur(...) 的顶部模糊条（PuiUi.kt 约 142~145 行附近的
 * Box(...clipToBounds().blur(...))）不再需要，可以删掉，避免两层模糊叠加。
 */

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 模糊半径随 y 变化：r = maxR * t * (2 - t)，t 从顶部的 1 渐变到 blurH 处的 0（缓出，越靠顶部越快达到强模糊）。 */
private const val PROGRESSIVE_BLUR_AGSL = """
uniform shader content;
uniform float2 size;
uniform float blurH;
uniform float maxR;

float hash(float2 p) {
    return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
}

half4 main(float2 c) {
    float t = clamp(1.0 - c.y / blurH, 0.0, 1.0);
    float r = maxR * t * (2.0 - t);
    if (r < 0.75) {
        return content.eval(c);
    }
    float rot = hash(c) * 6.2831853;
    half4 acc = half4(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 48; i++) {
        float fi = float(i);
        float a = fi * 2.3999632 + rot;
        float rad = sqrt((fi + 0.5) / 48.0) * r;
        float2 p = clamp(c + float2(cos(a), sin(a)) * rad,
                         float2(0.5, 0.5), size - float2(0.5, 0.5));
        float w = 1.0 - 0.5 * (rad / r);
        acc += half4(content.eval(p)) * half(w);
        wsum += w;
    }
    return acc / half(wsum);
}
"""

/** 默认的模糊区域高度：状态栏高度 + [extra]。 */
@Composable
fun rememberPuiTopBlurHeight(extra: Dp = 64.dp): Dp {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return top + extra
}

/**
 * 给「滚动内容」加顶部渐变模糊。放在内容自己的 modifier 链上即可，不影响触摸和滚动。
 *
 * @param blurHeight 模糊区域高度（从屏幕顶端算起）
 * @param maxRadius  最顶端的最大模糊半径；想更深就调大（例如 36.dp），更浅就调小
 */
@Composable
fun Modifier.progressiveTopBlur(
    blurHeight: Dp,
    maxRadius: Dp = 28.dp,
    /** 传入后，NavBar 所在区域额外叠一层系统原生 RenderEffect 模糊（见 NavBackdrop.blurEffectOver）。 */
    backdrop: NavBackdrop? = null,
): Modifier {
    val shader = remember { RuntimeShader(PROGRESSIVE_BLUR_AGSL) }
    return this.graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("blurH", blurHeight.toPx())
        shader.setFloatUniform("maxR", maxRadius.toPx())
        compositingStrategy = CompositingStrategy.Offscreen
        val top = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
        renderEffect = (backdrop?.blurEffectOver(top) ?: top).asComposeRenderEffect()
    }
}

/** 顶部渐隐遮罩：从 [color]（默认 surface）的 [topAlpha] 渐变到完全透明，与模糊区域等高。 */
@Composable
fun BoxScope.PuiTopFade(
    height: Dp,
    color: Color = MaterialTheme.colorScheme.surface,
    topAlpha: Float = 0.69f,
) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = topAlpha), color.copy(alpha = 0f))
                )
            )
    )
}
