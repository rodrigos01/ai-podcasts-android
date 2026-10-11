package com.rodrigos01.aipodcasts.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import com.rodrigos01.aipodcasts.wear.theme.AIPodcastsWearTheme
import com.rodrigos01.aipodcasts.wear.ui.WearApp

@UnstableApi
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIPodcastsWearTheme {
                WearApp()
            }
        }
    }
}
