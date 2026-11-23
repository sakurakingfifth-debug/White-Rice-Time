package com.panl.pui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.panl.balance.R

/**
 * PUI 原生控件集合。
 *
 * 这里刻意**不用** Material 的 `Switch` / `Slider` / `Icon`：
 * - M3 的开关是带图标的胶囊、滑块是「粗轨道 + 竖条把手 + 中间留缝」，都是 Expressive 的特征；
 * - PUI 的开关是细轨道 + 白色圆钮，滑块是细轨道 + 圆形把手、已选段实心连续；
 * - 图标是线性描边（1.8dp、圆头圆角），不是 Material 的实心图形。
 */

// ---------------- 开关 ----------------

@Composable
fun PuiSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val trackW = 44.dp
    val trackH = 26.dp
    val knob = 20.dp
    val inset = 3.dp

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.surfaceVariant.copy(alpha = 0.6f)
            checked -> scheme.primary
            else -> scheme.surfaceVariant
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "pui-switch-track"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackW - knob - inset else inset,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "pui-switch-thumb"
    )
    val thumbColor = if (enabled) Color.White else Color.White.copy(alpha = 0.7f)

    Box(
        modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) {
                // 开关的触感反馈：开/关用不同的震动类型
                haptic.performHapticFeedback(
                    if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                )
                onChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(knob)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

// ---------------- 滑块 ----------------

/**
 * 滑块刻度的步长（单位数）。
 *
 * 步长 3 个单位：轨道可用宽约 357dp，范围 20–100 共 80 个单位 → 约 4.5dp 一个单位，
 * 也就是**约 13.5dp 行程震一次**，中速拖动约 22 次/秒。
 * 触感用 `rememberTickHaptic()`（`CLOCK_TICK`），不用 Compose 的
 * `SegmentFrequentTick` —— 后者在 OPPO 上会退化成几乎感觉不到的 `TEXTURE_TICK`，
 * 详见 `PuiHaptics.kt` 的说明。
 */
private const val SLIDER_HAPTIC_STEP = 3

@Composable
fun PuiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 分段数；> 0 时在轨道上画刻度点（不含两端）。 */
    segments: Int = 0,
    /** 轨道高度，默认 24dp（[PuiSliderStyles.Pui]）。 */
    trackHeight: Dp = PuiSliderStyles.Pui.first,
    /** 圆钮直径，默认 16dp。小于轨道高度时两端形成同心圆环 —— 这是规范的样子。 */
    thumbSize: Dp = PuiSliderStyles.Pui.second
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = if (enabled) scheme.primary else scheme.outline.copy(alpha = 0.45f)
    val inactiveColor = if (enabled) scheme.surfaceVariant else scheme.surfaceVariant.copy(alpha = 0.5f)
    val thumbColor = if (enabled) Color.White else Color.White.copy(alpha = 0.7f)

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    // 按下：圆钮微放大 + 触点光效
    val tick = rememberTickHaptic()
    // 拖动时每跨过 SLIDER_HAPTIC_STEP 个单位震一次。判据是「显示值 ÷ 步长」的整数商，
    // 所以震动和界面上显示的 dp 数字严格对齐；直接比浮点差 2.0 会在 43.9→46.1 漏震、
    // 46.1→46.3 又重复震。
    // 所有滑块的 range 都是正数（20–100 / 50–100），整数除法即向下取整。
    var lastHapticBucket by remember {
        mutableIntStateOf(value.roundToInt() / SLIDER_HAPTIC_STEP)
    }
    var pressed by remember { mutableStateOf(false) }
    var glowPos by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "pui-slider-thumb"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (touching) 0.40f else 0f,
        animationSpec = tween(durationMillis = if (touching) 140 else 260),
        label = "pui-slider-glow"
    )

    val density = LocalDensity.current
    val thumbPx = with(density) { thumbSize.toPx() }
    val trackPx = with(density) { trackHeight.toPx() }
    // Canvas 的 draw lambda 不是 @Composable，主题色必须提前取出来
    val segmentDotColor = scheme.outline.copy(alpha = 0.35f)
    // 圆钮中心的端点位置：取「轨道半径」与「圆钮半径」的较大者。
    // 24dp 轨道 + 16dp 圆钮 → 12dp，0% 时已选段正好是以 12dp 为心的 24dp 圆，
    // 与 16dp 圆钮同心，四周各留 4dp 的环 —— 左右边距相等。
    val endInsetPx = maxOf(trackPx / 2f, thumbPx / 2f)
    fun valueAt(x: Float, width: Int): Float {
        val usable = (width - endInsetPx * 2).coerceAtLeast(1f)
        val f = ((x - endInsetPx) / usable).coerceIn(0f, 1f)
        return valueRange.start + f * span
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                // 光斑中心夹在轨道带里：手指按到滑块外（上下越界）也不会把光效带出去
                fun clampToTrack(p: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset {
                    val half = 12.dp.toPx()
                    val cy = size.height / 2f
                    return androidx.compose.ui.geometry.Offset(
                        p.x.coerceIn(0f, size.width.toFloat()),
                        p.y.coerceIn(cy - half, cy + half)
                    )
                }
                detectTapGestures(
                    onPress = { pos ->
                        pressed = true
                        touching = true
                        glowPos = clampToTrack(pos)
                        tryAwaitRelease()
                        pressed = false
                        touching = false
                    },
                    onTap = { pos ->
                        glowPos = clampToTrack(pos)
                        onValueChange(valueAt(pos.x, size.width))
                    }
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                fun clampToTrack(p: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset {
                    val half = 12.dp.toPx()
                    val cy = size.height / 2f
                    return androidx.compose.ui.geometry.Offset(
                        p.x.coerceIn(0f, size.width.toFloat()),
                        p.y.coerceIn(cy - half, cy + half)
                    )
                }
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        pressed = true
                        touching = true
                        glowPos = clampToTrack(pos)
                        lastHapticBucket =
                            valueAt(pos.x, size.width).roundToInt() / SLIDER_HAPTIC_STEP
                    },
                    onDragEnd = { pressed = false; touching = false },
                    onDragCancel = { pressed = false; touching = false }
                ) { change, _ ->
                    // 拖动时手指常常跑出滑块（上下偏移），光斑仍贴在轨道带上
                    glowPos = clampToTrack(change.position)
                    val v = valueAt(change.position.x, size.width)
                    // 每跨过 3 个单位震一下（和界面上显示的 dp / % 同步）
                    val bucket = v.roundToInt() / SLIDER_HAPTIC_STEP
                    if (bucket != lastHapticBucket) {
                        lastHapticBucket = bucket
                        tick()
                    }
                    onValueChange(v)
                }
            }
    ) {
        // 轨道 24dp、圆钮 16dp、触摸区 36dp（见 PuiSliderStyles）。
        val trackH = trackHeight.toPx()
        val cy = size.height / 2f
        val baseThumbR = thumbPx / 2f
        val thumbR = baseThumbR * thumbScale
        // 端点位置 = max(轨道半径, 圆钮半径)：24/16 时是 12dp，0% 时已选段是 24dp 的正圆
        val endInset = maxOf(trackH / 2f, baseThumbR)
        val left = endInset
        val right = size.width - endInset
        val usable = (right - left).coerceAtLeast(1f)
        val thumbX = left + usable * fraction
        val radius = CornerRadius(trackH / 2f, trackH / 2f)
        val trackTop = cy - trackH / 2f

        // 底轨
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackH),
            cornerRadius = radius
        )
        // 已选段：左端到轨道头，右端画到「圆钮中心 + endInset」—— 把圆钮右半边包住。
        // 0% 时宽度正好 = trackH，是一个以圆钮为心的正圆，四周各 4dp，不会再出现
        // 「一边 3dp 一边 5dp」或「葫芦腰」。
        val activeW = (thumbX + endInset).coerceIn(trackH, size.width)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, trackTop),
            size = Size(activeW, trackH),
            cornerRadius = radius
        )

        // 刻度点
        if (segments > 0) {
            val dotR = 1.6.dp.toPx()
            for (i in 1..segments) {
                val x = left + usable * (i.toFloat() / (segments + 1))
                drawCircle(
                    color = if (x < thumbX) Color.White.copy(alpha = 0.75f) else segmentDotColor,
                    radius = dotR,
                    center = Offset(x, cy)
                )
            }
        }

        // 触点光效：**裁剪到轨道胶囊内部**，绝不会溢出到滑块之外
        if (glowAlpha > 0.01f) {
            val clip = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, trackTop, size.width, trackTop + trackH),
                        radiusX = trackH / 2f,
                        radiusY = trackH / 2f
                    )
                )
            }
            clipPath(clip) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = glowAlpha),
                            Color.White.copy(alpha = glowAlpha * 0.4f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = glowPos,
                        radius = 56.dp.toPx()
                    ),
                    radius = 56.dp.toPx(),
                    center = glowPos
                )
            }
        }

        // 圆钮：纯白实心，不加投影 —— 靠「白 vs 彩色轨道」的对比就够清楚
        drawCircle(color = thumbColor, radius = thumbR, center = Offset(thumbX, cy))
    }
}

/**
 * 滑块的两种既定规格（轨道高 to 圆钮直径）。
 *
 * - [Pui]：原版规格，24dp 粗轨道 + 16dp 圆钮，0% / 100% 时形成一圈 4dp 的同心圆环；
 * - [Thin]：备选的细轨道（8dp + 22dp），圆钮比轨道大，两端与轨道端头齐平。
 */
object PuiSliderStyles {
    val Pui: Pair<Dp, Dp> = 24.dp to 16.dp
    val Thin: Pair<Dp, Dp> = 8.dp to 22.dp
}

// ---------------- Iconsax Duotone Filled 图标 ----------------
//
// 参考 Iconsax 的 Duotone / Filled 语言，构造规则：
//  - 纯实心色块，**不用描边勾轮廓**（这一点和 outline 风格是本质区别）；
//  - 两层同色：主形 100%（`main`），辅助形 30%（`sub`）—— Iconsax 的 duotone 就是深浅两块；
//  - 圆角给得比较足（0.06–0.28 s），边缘柔和；
//  - 24×24 网格，元素留 4%–6% 安全边距。
// 颜色默认取壁纸动态主色（LocalWallpaperAccent），跟着壁纸变。

/**
 * 图标枚举。
 *
 * [res] 非 0 时表示该图标改用 Iconsax Duotone Filled 矢量资源（`res/drawable/ic_pui_*.xml`），
 * 为 0 的仍是下面手绘的 PUI 内部图标（Element / Height / Touch 这些只有本库自己用）。
 */
enum class PuiIcon(val res: Int = 0) {
    /** 尺寸 / 布局：左侧高条 + 右侧上下两个方块（element-2） */
    Element,
    /** 其他 / 更多：油漆桶 + 刷子（桶身浅、桶沿/刷柄实） */
    Brush(R.drawable.ic_pui_brush),
    /** 任务栏高度：浅色竖条 + 上下实心箭头 */
    Height,
    /** 图标大小：浅色外框 + 内部实心方块 */
    IconSize,
    /** 触控区：同心圆盘 */
    Touch,
    /** Dock 圆角：内外两层胶囊 */
    Round,
    /** 强制任务栏：手机机身 + 底部任务栏条 */
    Phone,
    /** 两块圆角方块 + 中间通高竖条（check.svg：左块带一根短竖条） */
    Split,
    /** 竖向滑轨：浅色轨道 + 上下两条实心横轨（slider-vertical） */
    SliderVertical,
    /** Dock 光效：同心光晕 + 中心高光 */
    Glow(R.drawable.ic_pui_glow),
    /** 已选中：浅色外圆 + 实心内圆（同心圆包着实心圆） */
    Selected,

    // ---- 以下 6 个为 PuiBalance 追加（同一套 Iconsax Duotone Filled 语言，24dp 网格）----

    /** 钱包：浅色包身 + 右侧卡扣 + 卡面条 */
    Wallet(R.drawable.ic_pui_wallet),
    /** 余额：浅色圆盘 + 实心 ¥（比钱包更像「钱」，用于底部导航） */
    Coin(R.drawable.ic_pui_coin),
    /** 密钥：浅色匙环 + 实心匙柄与齿 */
    Key(R.drawable.ic_pui_key),
    /** 刷新：开口圆环 + 箭头（描边 + 实心三角） */
    Refresh(R.drawable.ic_pui_refresh),
    /** 显示密码：浅色眼形 + 实心瞳孔 */
    Eye(R.drawable.ic_pui_eye),
    /** 隐藏密码：眼形 + 斜杠 */
    EyeOff(R.drawable.ic_pui_eye_off),
    /** 设置：三条滑轨 + 错位把手 */
    Settings(R.drawable.ic_pui_settings),
    /** 通知：浅色铃身 + 实心铃舌 */
    Bell(R.drawable.ic_pui_bell),
    /** 后台常驻：实心闪电（Iconsax flash 的轮廓，两段折线围成） */
    Bolt(R.drawable.ic_pui_bolt),

    // ---- 以下 1 个为 PuiBalance 追加（同上语言）----

    /** 代码 / 仓库：左右尖括号 + 中间斜杠（`</>`），用于 GitHub 入口 */
    Code(R.drawable.ic_pui_code),

    /** 回收站：浅色桶身 + 实心桶盖与两道竖纹 */
    Trash,
    /** 信息：圆环 + 实心圆点与竖干 */
    Info,
    /** 版本 / 标签：浅色吊牌 + 实心穿孔与两道横纹 */
    Tag,
}

@Composable
fun PuiIconView(icon: PuiIcon, modifier: Modifier = Modifier, tint: Color? = null) {
    val wallpaper = LocalWallpaperAccent.current
    val color = tint
        ?: wallpaper.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.primary
    if (icon.res != 0) {
        // Iconsax Duotone Filled 资源：矢量里两层 fillAlpha 已经表达了明暗，tint 会把这套明暗保留下来
        Image(
            painter = painterResource(icon.res),
            contentDescription = null,
            modifier = modifier.size(24.dp),
            colorFilter = ColorFilter.tint(color)
        )
    } else {
        Canvas(modifier.size(24.dp)) {
            drawPuiIcon(icon, color)
        }
    }
}

private fun DrawScope.drawPuiIcon(icon: PuiIcon, color: Color) {
    val s = size.minDimension
    val main = color                        // 主形：实心
    val sub = color.copy(alpha = 0.45f)     // 辅助形：同色 45%（对齐 Iconsax 截图里的灰）

    fun p(x: Float, y: Float) = Offset(s * x, s * y)

    /** 圆角矩形色块。 */
    fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float, c: Color) = drawRoundRect(
        color = c,
        topLeft = p(l, t),
        size = Size(s * (r - l), s * (b - t)),
        cornerRadius = CornerRadius(s * rad, s * rad)
    )

    /** 圆盘。 */
    fun disc(cx: Float, cy: Float, rad: Float, c: Color) =
        drawCircle(c, s * rad, p(cx, cy))

    /** 三角形（实心）。 */
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, c: Color) {
        val path = Path().apply {
            moveTo(s * x1, s * y1)
            lineTo(s * x2, s * y2)
            lineTo(s * x3, s * y3)
            close()
        }
        drawPath(path, c)
    }

    /** 杏仁眼形：上下各一条三次贝塞尔，纵向 0.18–0.82（扁平比 1:0.64，24dp 下不会糊成一团）。 */
    fun eyePath(scale: Float) = Path().apply {
        moveTo(scale * 0.02f, scale * 0.50f)
        cubicTo(scale * 0.18f, scale * 0.28f, scale * 0.34f, scale * 0.18f, scale * 0.50f, scale * 0.18f)
        cubicTo(scale * 0.66f, scale * 0.18f, scale * 0.82f, scale * 0.28f, scale * 0.98f, scale * 0.50f)
        cubicTo(scale * 0.82f, scale * 0.72f, scale * 0.66f, scale * 0.82f, scale * 0.50f, scale * 0.82f)
        cubicTo(scale * 0.34f, scale * 0.82f, scale * 0.18f, scale * 0.72f, scale * 0.02f, scale * 0.50f)
        close()
    }

    when (icon) {
        // 左侧高条 + 右侧上下两个方块（按 element-2.svg 的坐标归一化）
        PuiIcon.Element -> {
            rr(0.104f, 0.083f, 0.458f, 0.917f, 0.10f, main)
            rr(0.542f, 0.083f, 0.896f, 0.458f, 0.10f, sub)
            rr(0.542f, 0.542f, 0.896f, 0.917f, 0.10f, sub)
        }
        // 油漆桶 + 刷子：按 brush-2.svg 的四条路径逐点还原（原始坐标 ÷24）
        //
        // 之前用「尖角梯形 + 两个圆角矩形」近似，丢了桶身两肩的圆弧（SVG 里半径约 3）
        // 和提手的半圆底，24dp 下看起来像个方箱而不是油漆桶。这里改成直接抄路径。
        PuiIcon.Brush -> {
            // ① 桶身（浅色）：上窄下宽（顶边 6.81–17.19，底边 4.5–19.5 @ y14），两肩 cubicTo 收圆
            val body = Path().apply {
                moveTo(s * 0.8404f, s * 0.2208f)                     // 右肩起
                lineTo(s * 0.8204f, s * 0.5838f)                     // 右壁下滑
                cubicTo(
                    s * 0.8179f, s * 0.5833f, s * 0.8154f, s * 0.5833f, s * 0.8125f, s * 0.5833f
                )
                lineTo(s * 0.1875f, s * 0.5833f)                     // 桶底
                cubicTo(
                    s * 0.1846f, s * 0.5833f, s * 0.1821f, s * 0.5833f, s * 0.1796f, s * 0.5838f
                )
                lineTo(s * 0.1596f, s * 0.2208f)                     // 左壁上升
                cubicTo(                                             // 左肩
                    s * 0.1521f, s * 0.1471f, s * 0.2100f, s * 0.0833f, s * 0.2838f, s * 0.0833f
                )
                lineTo(s * 0.7163f, s * 0.0833f)                     // 顶边
                cubicTo(                                             // 右肩
                    s * 0.7900f, s * 0.0833f, s * 0.8479f, s * 0.1471f, s * 0.8404f, s * 0.2208f
                )
                close()
            }
            drawPath(body, sub)

            // ② 两根刷毛（实色，下端半圆：宽 1.5，r 0.75）
            rr(0.3017f, 0.0833f, 0.3642f, 0.3229f, 0.03125f, main)
            rr(0.4688f, 0.0833f, 0.5313f, 0.1979f, 0.03125f, main)

            // ③ 桶沿 + 提手（实色，一条路径）：桶沿是 2.5–21.5 @ y14–18 的胶囊，
            //    提手是 9.5–14.5 的下垂半圆（圆心 y19.5，r 2.5）
            val rim = Path().apply {
                moveTo(s * 0.3958f, s * 0.8125f)
                lineTo(s * 0.3958f, s * 0.7500f)
                lineTo(s * 0.1875f, s * 0.7500f)
                cubicTo(
                    s * 0.1646f, s * 0.7500f, s * 0.1438f, s * 0.7408f, s * 0.1288f, s * 0.7254f
                )
                cubicTo(
                    s * 0.1133f, s * 0.7104f, s * 0.1042f, s * 0.6896f, s * 0.1042f, s * 0.6667f
                )
                cubicTo(
                    s * 0.1042f, s * 0.6238f, s * 0.1375f, s * 0.5879f, s * 0.1796f, s * 0.5838f
                )
                cubicTo(
                    s * 0.1821f, s * 0.5833f, s * 0.1846f, s * 0.5833f, s * 0.1875f, s * 0.5833f
                )
                lineTo(s * 0.8125f, s * 0.5833f)
                cubicTo(
                    s * 0.8154f, s * 0.5833f, s * 0.8179f, s * 0.5833f, s * 0.8204f, s * 0.5838f
                )
                cubicTo(
                    s * 0.8404f, s * 0.5854f, s * 0.8579f, s * 0.5942f, s * 0.8713f, s * 0.6079f
                )
                cubicTo(
                    s * 0.8883f, s * 0.6246f, s * 0.8975f, s * 0.6483f, s * 0.8954f, s * 0.6742f
                )
                cubicTo(
                    s * 0.8917f, s * 0.7179f, s * 0.8521f, s * 0.7500f, s * 0.8079f, s * 0.7500f
                )
                lineTo(s * 0.6042f, s * 0.7500f)
                lineTo(s * 0.6042f, s * 0.8125f)
                cubicTo(                                             // 提手右半圆
                    s * 0.6042f, s * 0.8700f, s * 0.5575f, s * 0.9167f, s * 0.5000f, s * 0.9167f
                )
                cubicTo(                                             // 提手左半圆
                    s * 0.4425f, s * 0.9167f, s * 0.3958f, s * 0.8700f, s * 0.3958f, s * 0.8125f
                )
                close()
            }
            drawPath(rim, main)
        }
        // 并排两支箭头：左实心上、右浅色下（Iconsax arrow-up-down 的构造）
        PuiIcon.Height -> {
            rr(0.29f, 0.30f, 0.41f, 0.72f, 0.06f, main)
            tri(0.35f, 0.10f, 0.51f, 0.36f, 0.19f, 0.36f, main)
            rr(0.59f, 0.28f, 0.71f, 0.70f, 0.06f, sub)
            tri(0.65f, 0.90f, 0.49f, 0.64f, 0.81f, 0.64f, sub)
        }
        // 浅色外框 + 内部实心方块
        PuiIcon.IconSize -> {
            rr(0.04f, 0.04f, 0.96f, 0.96f, 0.24f, sub)
            rr(0.30f, 0.30f, 0.70f, 0.70f, 0.10f, main)
        }
        // 同心三层：实-浅-实，读作「触点 / 目标」（和 Selected 的两层区分开）
        PuiIcon.Touch -> {
            disc(0.50f, 0.50f, 0.46f, main)
            disc(0.50f, 0.50f, 0.34f, sub)
            disc(0.50f, 0.50f, 0.16f, main)
        }
        // 内外两层胶囊
        PuiIcon.Round -> {
            rr(0.02f, 0.22f, 0.98f, 0.78f, 0.28f, sub)
            rr(0.16f, 0.34f, 0.84f, 0.66f, 0.16f, main)
        }
        // 手机：浅色机身 + 实心听筒条 + 实心 Home 点（按 mobile.svg 的坐标归一化）
        PuiIcon.Phone -> {
            rr(0.167f, 0.083f, 0.833f, 0.917f, 0.080f, sub)
            rr(0.417f, 0.198f, 0.583f, 0.260f, 0.031f, main)
            disc(0.500f, 0.731f, 0.073f, main)
        }
        // 两块圆角方块 + 中间通高竖条：按 check.svg 的四条路径逐点还原（原始坐标 ÷24）
        //
        // 关键点：左右两块只有**朝外那一侧**是 r4 圆角，内侧（x12 / x15）是直角，
        // 所以不能用统一圆角的 rr()，要用逐角半径的 RoundRect。
        PuiIcon.Split -> {
            // ① 中间通高竖条：x 14.25–15.75，y 2–22，r 0.75
            rr(0.59375f, 0.08333f, 0.65625f, 0.91667f, 0.03125f, main)

            // ② 左块：x 2–12，y 4–20，左上 / 左下 r4，右侧直角
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(s * 0.08333f, s * 0.16667f, s * 0.50000f, s * 0.83333f),
                            topLeft = CornerRadius(s * 0.16667f),
                            bottomLeft = CornerRadius(s * 0.16667f),
                        )
                    )
                },
                sub
            )

            // ③ 右块：x 15–22，y 4–20，右上 / 右下 r4，左侧直角
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(s * 0.62500f, s * 0.16667f, s * 0.91667f, s * 0.83333f),
                            topRight = CornerRadius(s * 0.16667f),
                            bottomRight = CornerRadius(s * 0.16667f),
                        )
                    )
                },
                sub
            )

            // ④ 左块内的短竖条：x 6.25–7.75，y 7.75–16.25，r 0.75
            rr(0.26042f, 0.32292f, 0.32292f, 0.67708f, 0.03125f, main)
        }
        // 竖向滑轨：浅色轨道 + 上下两条实心横轨（按 slider-vertical.svg 的坐标归一化）
        PuiIcon.SliderVertical -> {
            rr(0.115f, 0.192f, 0.885f, 0.808f, 0.125f, sub)
            rr(0.180f, 0.083f, 0.820f, 0.146f, 0.031f, main)
            rr(0.180f, 0.854f, 0.820f, 0.917f, 0.031f, main)
        }
        // 中心亮点 + 四向光线 + 淡光晕
        PuiIcon.Glow -> {
            disc(0.50f, 0.50f, 0.46f, color.copy(alpha = 0.12f))
            rr(0.455f, 0.02f, 0.545f, 0.17f, 0.045f, sub)
            rr(0.455f, 0.83f, 0.545f, 0.98f, 0.045f, sub)
            rr(0.02f, 0.455f, 0.17f, 0.545f, 0.045f, sub)
            rr(0.83f, 0.455f, 0.98f, 0.545f, 0.045f, sub)
            disc(0.50f, 0.50f, 0.23f, main)
        }
        // 同心圆包着实心圆
        PuiIcon.Selected -> {
            disc(0.50f, 0.50f, 0.46f, sub)
            disc(0.50f, 0.50f, 0.24f, main)
        }
        // 钱包：浅色包身 + 卡面条（实）+ 右侧卡扣（实）+ 扣上的圆点（浅）
        PuiIcon.Wallet -> {
            rr(0.04f, 0.16f, 0.96f, 0.88f, 0.22f, sub)
            rr(0.15f, 0.26f, 0.62f, 0.335f, 0.035f, main)
            rr(0.66f, 0.40f, 0.99f, 0.64f, 0.11f, main)
            disc(0.845f, 0.52f, 0.045f, sub)
        }
        // 余额：浅色圆盘 + 实心 ¥（两撇 + 竖干 + 两横，圆头描边）
        PuiIcon.Coin -> {
            disc(0.50f, 0.50f, 0.46f, sub)
            val stroke = s * 0.075f
            drawLine(main, p(0.35f, 0.28f), p(0.50f, 0.47f), stroke, StrokeCap.Round)
            drawLine(main, p(0.65f, 0.28f), p(0.50f, 0.47f), stroke, StrokeCap.Round)
            drawLine(main, p(0.50f, 0.47f), p(0.50f, 0.73f), stroke, StrokeCap.Round)
            drawLine(main, p(0.36f, 0.545f), p(0.64f, 0.545f), stroke, StrokeCap.Round)
            drawLine(main, p(0.36f, 0.645f), p(0.64f, 0.645f), stroke, StrokeCap.Round)
        }
        // 密钥：浅色匙环 + 实心内环、匙柄、两道齿
        PuiIcon.Key -> {
            disc(0.28f, 0.36f, 0.22f, sub)
            disc(0.28f, 0.36f, 0.10f, main)
            rr(0.44f, 0.31f, 0.95f, 0.41f, 0.05f, main)
            rr(0.70f, 0.41f, 0.78f, 0.58f, 0.04f, main)
            rr(0.58f, 0.41f, 0.66f, 0.52f, 0.04f, main)
        }
        // 刷新：开口圆环（描边、圆头）+ 右上箭头
        PuiIcon.Refresh -> {
            drawArc(
                color = main,
                startAngle = 305f,
                sweepAngle = 250f,
                useCenter = false,
                topLeft = p(0.16f, 0.16f),
                size = Size(s * 0.68f, s * 0.68f),
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round)
            )
            tri(0.64f, 0.09f, 0.64f, 0.35f, 0.92f, 0.22f, main)
        }
        // 显示：浅色杏仁眼形 + 实心瞳孔
        PuiIcon.Eye -> {
            drawPath(eyePath(s), sub)
            disc(0.50f, 0.50f, 0.14f, main)
        }
        // 隐藏：同眼形去掉瞳孔 + 一道实心斜杠
        PuiIcon.EyeOff -> {
            drawPath(eyePath(s), sub)
            drawLine(
                color = main,
                start = p(0.16f, 0.14f),
                end = p(0.84f, 0.86f),
                strokeWidth = s * 0.075f,
                cap = StrokeCap.Round
            )
        }
        // 通知：浅色铃身 + 顶部提钮 + 实心铃舌
        PuiIcon.Bell -> {
            val bell = Path().apply {
                moveTo(s * 0.50f, s * 0.15f)
                cubicTo(s * 0.67f, s * 0.15f, s * 0.77f, s * 0.28f, s * 0.77f, s * 0.45f)
                lineTo(s * 0.77f, s * 0.58f)
                cubicTo(s * 0.77f, s * 0.65f, s * 0.81f, s * 0.70f, s * 0.86f, s * 0.73f)
                lineTo(s * 0.14f, s * 0.73f)
                cubicTo(s * 0.19f, s * 0.70f, s * 0.23f, s * 0.65f, s * 0.23f, s * 0.58f)
                lineTo(s * 0.23f, s * 0.45f)
                cubicTo(s * 0.23f, s * 0.28f, s * 0.33f, s * 0.15f, s * 0.50f, s * 0.15f)
                close()
            }
            drawPath(bell, sub)
            disc(0.50f, 0.105f, 0.045f, main)
            disc(0.50f, 0.825f, 0.075f, main)
        }
        // 设置：三条浅色滑轨 + 错位实心把手
        PuiIcon.Settings -> {
            rr(0.06f, 0.235f, 0.94f, 0.305f, 0.035f, sub)
            rr(0.06f, 0.465f, 0.94f, 0.535f, 0.035f, sub)
            rr(0.06f, 0.695f, 0.94f, 0.765f, 0.035f, sub)
            disc(0.33f, 0.27f, 0.085f, main)
            disc(0.66f, 0.50f, 0.085f, main)
            disc(0.42f, 0.73f, 0.085f, main)
        }
        // 后台常驻：闪电。主体实心，右下 6% / 5% 处压一块同形浅色残影（duotone 的辅助形）
        PuiIcon.Bolt -> {
            fun boltPath(dx: Float, dy: Float) = Path().apply {
                moveTo(s * (0.585f + dx), s * (0.055f + dy))
                lineTo(s * (0.185f + dx), s * (0.545f + dy))
                lineTo(s * (0.455f + dx), s * (0.545f + dy))
                lineTo(s * (0.415f + dx), s * (0.945f + dy))
                lineTo(s * (0.815f + dx), s * (0.455f + dy))
                lineTo(s * (0.545f + dx), s * (0.455f + dy))
                close()
            }
            drawPath(boltPath(0.06f, 0.05f), sub)
            drawPath(boltPath(0f, 0f), main)
        }
        // 代码 / 仓库：两个尖括号（实心描边）+ 中间斜杠（浅色）
        PuiIcon.Code -> {
            val stroke = s * 0.085f
            drawLine(main, p(0.36f, 0.24f), p(0.15f, 0.50f), stroke, StrokeCap.Round)
            drawLine(main, p(0.15f, 0.50f), p(0.36f, 0.76f), stroke, StrokeCap.Round)
            drawLine(main, p(0.64f, 0.24f), p(0.85f, 0.50f), stroke, StrokeCap.Round)
            drawLine(main, p(0.85f, 0.50f), p(0.64f, 0.76f), stroke, StrokeCap.Round)
            drawLine(sub, p(0.575f, 0.19f), p(0.425f, 0.81f), stroke, StrokeCap.Round)
        }
        // 回收站：浅色桶身 + 实心桶盖、提手与两道竖纹
        PuiIcon.Trash -> {
            rr(0.26f, 0.34f, 0.74f, 0.86f, 0.09f, sub)
            rr(0.16f, 0.24f, 0.84f, 0.33f, 0.045f, main)
            rr(0.41f, 0.13f, 0.59f, 0.24f, 0.035f, main)
            val stroke = s * 0.07f
            drawLine(main, p(0.42f, 0.46f), p(0.42f, 0.74f), stroke, StrokeCap.Round)
            drawLine(main, p(0.58f, 0.46f), p(0.58f, 0.74f), stroke, StrokeCap.Round)
        }
        // 信息：圆环 + 实心圆点与竖干
        PuiIcon.Info -> {
            drawCircle(main, s * 0.40f, p(0.50f, 0.50f), style = Stroke(width = s * 0.085f))
            disc(0.50f, 0.33f, 0.052f, main)
            drawLine(main, p(0.50f, 0.46f), p(0.50f, 0.70f), s * 0.085f, StrokeCap.Round)
        }
        // 版本 / 标签：浅色吊牌 + 实心穿孔与两道横纹
        PuiIcon.Tag -> {
            rr(0.24f, 0.14f, 0.76f, 0.86f, 0.14f, sub)
            disc(0.50f, 0.30f, 0.062f, main)
            rr(0.34f, 0.47f, 0.66f, 0.545f, 0.038f, main)
            rr(0.34f, 0.62f, 0.56f, 0.695f, 0.038f, main)
        }
    }
}
