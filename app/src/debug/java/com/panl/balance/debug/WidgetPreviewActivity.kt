package com.panl.balance.debug

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.panl.balance.MainActivity
import com.panl.balance.R
import com.panl.balance.data.BalanceSnapshot
import com.panl.balance.data.CurrencyBalance
import com.panl.balance.widget.BalanceWidgets

/** 调试用：把 5 种尺寸的插件按真实格子大小渲染出来。只在 debug 包里存在。 */
class WidgetPreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val snapshot = BalanceSnapshot(
            available = true,
            items = listOf(CurrencyBalance("CNY", 128_450.00, 60_000.0, 68_450.0)),
            fetchedAt = System.currentTimeMillis() - 3 * 60_000,
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val refresh = PendingIntent.getBroadcast(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
            setBackgroundColor(Color.parseColor("#F6F8F7"))
        }

        fun label(text: String, top: Int = 18) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#404943"))
            setPadding(0, dp(top), 0, dp(8))
        }

        fun card(spec: Spec): FrameLayout {
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(spec.w), dp(spec.h))
            }
            val views = BalanceWidgets.build(
                this, spec.layout, snapshot, true,
                BalanceWidgets.accentColor(this, true), openApp, refresh
            )
            frame.addView(views.apply(this, frame))
            return frame
        }

        // 第一行：1×1 与 2×1 并排，让 5 种尺寸能在一屏内看全
        column.addView(label("1×1 / 2×1", top = 0))
        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstRow.addView(
            card(Spec(R.layout.widget_1x1, 70, 70)).apply {
                layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)).apply { marginEnd = dp(14) }
            }
        )
        firstRow.addView(card(Spec(R.layout.widget_2x1, 150, 70)))
        column.addView(firstRow)

        column.addView(label("2×2"))
        column.addView(card(Spec(R.layout.widget_2x2, 150, 150)))

        column.addView(label("4×2"))
        column.addView(card(Spec(R.layout.widget_4x2, 310, 150)))

        column.addView(label("4×4"))
        column.addView(card(Spec(R.layout.widget_4x4, 310, 310)))

        setContentView(ScrollView(this).apply { addView(column) })
    }

    private data class Spec(val layout: Int, val w: Int, val h: Int)
}
