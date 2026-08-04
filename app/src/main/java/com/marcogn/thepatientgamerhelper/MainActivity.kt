package com.marcogn.thepatientgamerhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.marcogn.thepatientgamerhelper.domain.model.ThemeMode
import com.marcogn.thepatientgamerhelper.ui.navigation.ThePatientGamerHelperNavGraph
import com.marcogn.thepatientgamerhelper.ui.theme.ThePatientGamerHelperTheme
import com.marcogn.thepatientgamerhelper.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThePatientGamerHelperApp()
        }
    }
}

@Composable
private fun ThePatientGamerHelperApp(themeViewModel: ThemeViewModel = hiltViewModel()) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SISTEMA -> isSystemInDarkTheme()
        ThemeMode.CHIARO -> false
        ThemeMode.SCURO -> true
    }
    ThePatientGamerHelperTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ThePatientGamerHelperNavGraph()
        }
    }
}
