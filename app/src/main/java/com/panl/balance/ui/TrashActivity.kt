package com.panl.balance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panl.balance.data.HistoryEntry
import com.panl.balance.data.Prefs
import com.panl.balance.data.Trash
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGhostButton
import com.panl.pui.PuiGroup
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiTopFade
import com.panl.pui.progressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import androidx.compose.ui.platform.LocalContext

/**
 * 回收站页。
 *
 * 余额记录点「清空」时整批挪进这里，**只有在这个页面里彻底删除才是真删**。
 * 和「外观与导航栏」一样是独立 Activity，走 ColorOS 无缝动画由设置页那一行放大进来。
 */
class TrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                TrashScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val entries = Trash.entries
    val blurHeight = rememberPuiTopBlurHeight(extra = TopBlurExtra)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var confirmClear by remember { mutableStateOf(false) }
    var confirmRestoreAll by remember { mutableStateOf(false) }
    val pageScroll = rememberScrollState()
    var pendingPurge by remember { mutableStateOf<HistoryEntry?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (Appearance.blurEnabled) {
                        Modifier.progressiveTopBlur(blurHeight, maxRadius = 16.dp)
                    } else {
                        Modifier
                    }
                )
                .verticalScroll(pageScroll)
                .padding(start = 16.dp, end = 16.dp, top = blurHeight + 4.dp, bottom = 104.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .offset(x = (-8).dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Chevron(size = 18.dp, color = scheme.onSurface, direction = -1)
                }
                PuiPageTitle("回收站")
            }

            if (entries.isEmpty()) {
                PuiGroup(title = null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "回收站是空的",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "清空余额记录后，记录会先放到这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                PuiGroup(title = "共 ${entries.size} 条") {
                    entries.forEachIndexed { index, entry ->
                        TrashRow(
                            entry = entry,
                            showDivider = index != entries.lastIndex,
                            onRestore = {
                                Trash.remove(entry)
                                prefs.history = (prefs.history + entry).sortedByDescending { it.at }
                            },
                            onPurge = { pendingPurge = entry }
                        )
                    }
                }

                PuiGhostButton("清空回收站", Modifier.fillMaxWidth()) { confirmClear = true }
            }

        }

        PuiTopFade(height = blurHeight, topAlpha = 0.5f)

        // 常驻胶囊：整站一键恢复
        if (entries.isNotEmpty()) {
            // 底部渐隐：列表继续往下滚时从后面淡出，胶囊才不会透出底下的文字
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(170.dp + bottomInset)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            // 到胶囊所在的区域就必须完全不透，否则半透明的胶囊会透出底下的记录
                            0.62f to scheme.background,
                            1f to scheme.background
                        )
                    )
            )
            // 滚动时收起，停下来再回来（参考那颗按钮在列表滚动时不会一直压在内容上）
            AnimatedVisibility(
                visible = !pageScroll.isScrollInProgress,
                enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                RestoreAllPill(
                    text = "一键恢复",
                    modifier = Modifier.padding(
                        start = 40.dp,
                        end = 40.dp,
                        bottom = 28.dp + bottomInset
                    ),
                    onClick = { confirmRestoreAll = true }
                )
            }
        }
    }

    if (confirmRestoreAll) {
        AppDialog(
            title = "一键恢复？",
            onDismissRequest = { confirmRestoreAll = false },
            confirmText = "恢复",
            onConfirm = {
                val all = entries
                Trash.clear()
                prefs.history = (prefs.history + all).sortedByDescending { it.at }
                confirmRestoreAll = false
            }
        ) {
            Text(
                "回收站里的 ${entries.size} 条记录会全部搬回「余额记录」，回收站随即清空。\n\n" +
                    "记录本身的时间不变，回去后按时间倒序排列。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空回收站？",
            body = "这 ${entries.size} 条记录会被彻底删除，无法恢复。",
            confirmText = "彻底删除",
            onDismiss = { confirmClear = false },
            onConfirm = {
                Trash.clear()
                confirmClear = false
            }
        )
    }

    pendingPurge?.let { entry ->
        ConfirmDialog(
            title = "彻底删除这条记录？",
            body = "${Fmt.clock(entry.at)} · ${Fmt.symbol(entry.currency)}${Fmt.amount(entry.total)}\n删除后无法恢复。",
            confirmText = "彻底删除",
            onDismiss = { pendingPurge = null },
            onConfirm = {
                Trash.remove(entry)
                pendingPurge = null
            }
        )
    }
}

/** 一条回收站记录：时间 + 金额，右侧「恢复 / 删除」。 */
@Composable
private fun TrashRow(
    entry: HistoryEntry,
    showDivider: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    Fmt.clock(entry.at),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${Fmt.symbol(entry.currency)}${Fmt.amount(entry.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            PuiLinkButton("恢复", onClick = onRestore)
            PuiLinkButton("删除", onClick = onPurge, color = scheme.error)
            Spacer(Modifier.width(4.dp))
        }
        if (showDivider) PuiDivider(startIndent = 16.dp)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
        destructive = true
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 底部胶囊按钮，样式对齐「小布指令」详情页那颗「添加至自动指令」：
 *
 * - **不透明**：底色是 primary 与页面底色按 62% 混出来的实色。之前直接用半透明 primary，
 *   列表滚到下面时会从按钮里透出来，看着像残影。
 * - **按压涟漪**：参考视频里按下时是一圈柔和的白色圆晕缓慢扩散（约 600ms）再淡出，
 *   不是 Material 那种一下就没的 ripple。这里自己用 `Animatable` 画一圈径向扩散的白色圆。
 */
@Composable
private fun RestoreAllPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // 62% 主题色压白底 ≈ 参考色 #7FA37F；但主题色本身偏灰，混出来会发闷，
    // 所以再把彩度往外推一点（远离同亮度的灰），视觉上才是参考那种「鲜一点的莫兰迪绿」
    val base = lerp(scheme.background, scheme.primary, 0.62f)
    // 注意：Color 的 lerp 会把 fraction 截断到 0..1，想「外推」提高彩度必须自己算
    val gray = (base.red + base.green + base.blue) / 3f
    fun boost(c: Float) = (gray + (c - gray) * 1.5f).coerceIn(0f, 1f)
    val fill = Color(boost(base.red), boost(base.green), boost(base.blue))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 扩散（radius）和亮度（alpha）分开：按住时圆晕一直在，松手才淡掉
    val spread = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    LaunchedEffect(pressed) {
        if (pressed) {
            spread.snapTo(0f)
            glow.snapTo(0f)
            launch { glow.animateTo(1f, tween(durationMillis = 130)) }
            launch { spread.animateTo(1f, tween(durationMillis = 620, easing = LinearOutSlowInEasing)) }
        } else {
            glow.animateTo(0f, tween(durationMillis = 280))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .drawWithContent {
                drawContent()
                val a = glow.value
                if (a > 0f) {
                    // 用径向渐变做「柔光」：中心稍亮、边缘化开，比实心白圆更像参考里那层雾状的光
                    val r = size.height * 0.40f + size.width * 0.62f * spread.value
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.42f * a),
                                Color.White.copy(alpha = 0.16f * a),
                                Color.White.copy(alpha = 0f)
                            ),
                            center = center,
                            radius = r
                        ),
                        radius = r,
                        center = center
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            ),
            color = Color.White
        )
    }
}
