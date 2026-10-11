package com.rodrigos01.aipodcasts

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import com.rodrigos01.aipodcasts.ui.navigation.PodcastNavGraph
import com.rodrigos01.aipodcasts.ui.navigation.Screen
import com.rodrigos01.aipodcasts.ui.theme.AIPodcastsTheme

@UnstableApi
// FragmentActivity (a ComponentActivity) because the cast button's device dialog is a fragment.
class MainActivity : FragmentActivity() {

    // Bumped each time the media notification asks to open the full player.
    private var openPlayerSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

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
                    startDestination = startDestination,
                    openPlayerSignal = openPlayerSignal
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            openPlayerSignal++
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.rodrigos01.aipodcasts.OPEN_PLAYER"
    }
}
