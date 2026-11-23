package com.panl.balance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** 「软件更新」页：同样由设置页那一行经无缝动画放大进入。 */
class OtaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BalanceTheme {
                OtaScreen(onBack = { finish() })
            }
        }
    }
}
