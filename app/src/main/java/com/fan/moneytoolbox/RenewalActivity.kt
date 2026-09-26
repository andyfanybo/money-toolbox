package com.fan.moneytoolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fan.moneytoolbox.ui.RenewalScreen
import com.fan.moneytoolbox.ui.theme.MoneyBoxTheme

class RenewalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoneyBoxTheme {
                val viewModel: RenewalViewModel = viewModel()
                RenewalScreen(viewModel, onBack = { finish() })
            }
        }
    }
}
