package com.marcogn.gamereviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.marcogn.gamereviewer.ui.navigation.GameReviewerNavGraph
import com.marcogn.gamereviewer.ui.theme.GameReviewerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameReviewerApp()
        }
    }
}

@Composable
private fun GameReviewerApp() {
    GameReviewerTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GameReviewerNavGraph()
        }
    }
}
