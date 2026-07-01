package com.alexpo.grammermate.v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alexpo.grammermate.v2.ui.GrammarMateApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivityV2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrammarMateApp()
        }
    }
}
