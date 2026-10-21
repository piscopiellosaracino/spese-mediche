package it.spesemediche

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import it.spesemediche.ui.AppRoot
import it.spesemediche.ui.AppViewModel
import it.spesemediche.ui.theme.SpeseTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpeseTheme { AppRoot(vm) }
        }
    }
}
