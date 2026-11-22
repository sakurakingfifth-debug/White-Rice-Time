package com.panl.balance.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 回收站：被清空的余额记录先放这里，只有在回收站里彻底删除才算真删。
 *
 * 用单例 + Compose 状态，而不是让回收站页面自己读 SharedPreferences —— 回收站是独立
 * Activity，设置页也要显示「回收站 (n 条)」，两边必须看到同一份数据并且即时同步。
 */
object Trash {

    var entries by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    private var prefs: Prefs? = null

    fun load(context: Context) {
        val p = Prefs(context.applicationContext)
        prefs = p
        entries = p.trash
    }

    /** 整批收进来（清空余额记录时调用），排在已有内容前面。 */
    fun addAll(list: List<HistoryEntry>) {
        if (list.isEmpty()) return
        entries = list + entries
        prefs?.trash = entries
    }

    /** 彻底删除一条 —— 到这一步才真的没了。 */
    fun remove(entry: HistoryEntry) {
        entries = entries.filterNot { it == entry }
        prefs?.trash = entries
    }

    /** 清空回收站。 */
    fun clear() {
        entries = emptyList()
        prefs?.trash = emptyList()
    }
}
