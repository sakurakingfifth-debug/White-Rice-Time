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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = blurHeight + 4.dp, bottom = 32.dp + bottomInset),
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

            Text(
                "彻底删除之后无法恢复；只想留着看的话，可以点「恢复」把记录搬回余额记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        PuiTopFade(height = blurHeight, topAlpha = 0.5f)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
