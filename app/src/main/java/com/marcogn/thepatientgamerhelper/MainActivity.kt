package com.marcogn.thepatientgamerhelper

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

/**
 * `AppCompatActivity`, non `ComponentActivity`: per Compose puro sarebbe la scelta di default, ma
 * `AppCompatDelegate.setApplicationLocales()` (selettore lingua, Fase 5) richiede esplicitamente
 * questa base class — con `ComponentActivity` il cambio lingua viene silenziosamente ignorato,
 * vedi `docs/decisioni-implementazione.md`. Non introduce View/XML: `setContent {}` resta l'unico
 * punto di ingresso della UI.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
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
