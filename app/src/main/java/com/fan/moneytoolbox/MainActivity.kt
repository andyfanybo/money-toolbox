package com.fan.moneytoolbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fan.moneytoolbox.ui.HomeScreen
import com.fan.moneytoolbox.ui.ParkingScreen
import com.fan.moneytoolbox.ui.theme.MoneyBoxTheme
import java.io.Serializable

class MainActivity : ComponentActivity() {

    private enum class Screen : Serializable { Home, Parking }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MoneyBoxTheme {
                val viewModel: ParkingViewModel = viewModel()
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                BackHandler(enabled = screen == Screen.Parking) { screen = Screen.Home }
                when (screen) {
                    Screen.Home -> HomeScreen(
                        viewModel = viewModel,
                        onOpenParking = { screen = Screen.Parking },
                        onOpenRenewals = { startActivity(Intent(this, RenewalActivity::class.java)) },
                        onOpenPdf = { startActivity(Intent(this, PdfToolsActivity::class.java)) },
                    )
                    Screen.Parking -> ParkingScreen(viewModel = viewModel, onBack = { screen = Screen.Home })
                }
            }
        }
    }
}
