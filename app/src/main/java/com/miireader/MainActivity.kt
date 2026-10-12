package com.miireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.miireader.di.ViewModelFactory
import com.miireader.ui.navigation.AppNavigation
import com.miireader.ui.theme.MiiReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MiiReaderApplication
        val container = app.container

        setContent {
            val viewModelFactory = remember { ViewModelFactory(container) }
            val preferences by container.userPreferencesRepository.preferences.collectAsState(
                initial = com.miireader.data.preferences.UserPreferences()
            )

            MiiReaderTheme(warmBackground = preferences.warmBackground) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        viewModelFactory = viewModelFactory
                    )
                }
            }
        }
    }
}
