package com.example.auraflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.example.auraflow.ui.chat.AuraFlowViewModel
import com.example.auraflow.ui.chat.ShadowOSScreen
import com.example.auraflow.ui.theme.AuraFlowTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AuraFlowViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AuraFlowTheme {
                ShadowOSScreen(viewModel = viewModel)
            }
        }
    }
}