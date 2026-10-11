package com.rodrigos01.aipodcasts.ui.components

import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

/**
 * The standard Chromecast button, shown only while there is something to cast to: a cast device
 * is discoverable on the network, or a cast session is already connected (so it can be ended).
 * Renders nothing otherwise, and if Cast isn't available at all (no Play Services). Casting
 * itself happens in PodcastPlaybackService, which follows the cast session.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val castContext = remember(context) { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
        ?: return
    val devicesAvailable = rememberCastDevicesAvailable(castContext)
    if (!devicesAvailable) return

    AndroidView(
        modifier = modifier.size(48.dp),
        factory = { ctx ->
            // MediaRouteButton needs an AppCompat theme; the app's own theme isn't one.
            val themed = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(themed).also { button ->
                val ready = runCatching { CastButtonFactory.setUpMediaRouteButton(ctx, button) }.isSuccess
                if (!ready) button.visibility = View.GONE
            }
        }
    )
}

/**
 * Whether a cast device can currently be picked, or one is connected. Asks the router for active
 * discovery while the caller is on screen (it is otherwise only passive) and follows route
 * changes. The device list is read from the router directly rather than trusting the
 * MediaRouteButton's own visibility, which stays up for system routes too.
 */
@Composable
private fun rememberCastDevicesAvailable(castContext: CastContext): Boolean {
    val context = LocalContext.current
    var available by remember { mutableStateOf(false) }

    DisposableEffect(castContext) {
        val router = MediaRouter.getInstance(context)
        val selector = castContext.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            )
            .build()

        fun refresh() {
            available = !router.selectedRoute.isDefaultOrBluetooth ||
                router.routes.any { it.matchesSelector(selector) && !it.isDefaultOrBluetooth }
        }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(
                router: MediaRouter,
                selected: MediaRouter.RouteInfo,
                reason: Int
            ) = refresh()
            override fun onRouteUnselected(
                router: MediaRouter,
                unselected: MediaRouter.RouteInfo,
                reason: Int
            ) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
        onDispose { router.removeCallback(callback) }
    }
    return available
}
