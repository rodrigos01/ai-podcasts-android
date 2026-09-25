package com.rodrigos01.aipodcasts.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.rodrigos01.aipodcasts.player.PodcastAudioController
import com.rodrigos01.aipodcasts.ui.screens.player.PlayerSheet

val LocalContentPadding = compositionLocalOf { PaddingValues() }

@UnstableApi
@Composable
fun PlayerScaffold(
    audioController: PodcastAudioController,
    modifier: Modifier = Modifier,
    showBottomBar: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    var showPlayerSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        bottomBar = {
            if (showBottomBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    MiniPlayerBar(
                        audioController = audioController,
                        onOpenFullPlayer = { showPlayerSheet = true }
                    )
                    bottomBar()
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalContentPadding provides padding) {
            content(padding)
        }
    }

    if (showPlayerSheet) {
        PlayerSheet(
            audioController = audioController,
            onDismiss = { showPlayerSheet = false }
        )
    }
}
