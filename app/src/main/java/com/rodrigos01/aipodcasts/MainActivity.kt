package com.rodrigos01.aipodcasts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.rodrigos01.aipodcasts.ui.navigation.PodcastNavGraph
import com.rodrigos01.aipodcasts.ui.navigation.Screen
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = AIPodcastsApplication.instance
        val authRepo = app.authRepository
        val settingsRepo = app.settingsRepository

        // Ensure any previous anonymous guest session is cleared
        authRepo.clearIfAnonymous()

        setContent {
            val themeMode by settingsRepo.themeModeFlow.collectAsState(initial = "system")
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            AIPodcastsTheme(darkTheme = isDark) {
                val navController = rememberNavController()
                val startDestination = if (authRepo.currentUser != null) {
                    Screen.Home.route
                } else {
                    Screen.Auth.route
                }

                PodcastNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
