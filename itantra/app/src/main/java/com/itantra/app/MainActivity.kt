package com.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itantra.app.ui.theme.ITantraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.init(applicationContext)
        setContent {
            ITantraApp()
        }
    }
}

@Composable
fun ITantraApp() {
    ITantraTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            ITantraNavHost()
        }
    }
}
