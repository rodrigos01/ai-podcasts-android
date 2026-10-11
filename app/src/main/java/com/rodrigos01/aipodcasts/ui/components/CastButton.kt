package com.rodrigos01.aipodcasts.ui.components

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * The standard Chromecast button: shows when cast devices are around, opens the device picker
 * and, while connected, the disconnect dialog. Renders nothing if Cast isn't available (no Play
 * Services). Casting itself happens in PodcastPlaybackService, which follows the cast session.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.size(48.dp),
        factory = { context ->
            // MediaRouteButton needs an AppCompat theme; the app's own theme isn't one.
            val themed = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(themed).also { button ->
                val ready = runCatching {
                    CastContext.getSharedInstance(context)
                    CastButtonFactory.setUpMediaRouteButton(context, button)
                }.isSuccess
                if (!ready) button.visibility = android.view.View.GONE
            }
        }
    )
}
