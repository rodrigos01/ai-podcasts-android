package com.rodrigos01.aipodcasts.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import androidx.mediarouter.media.MediaRouter.RouteInfo
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.rodrigos01.aipodcasts.R

/**
 * Shows where audio is playing and lets the user move it: an icon for the selected output (this
 * phone, a speaker, a TV, Bluetooth headphones, ...) that opens the device picker, or, while
 * playing on another device, that device's controls (volume, disconnect). Shown only while there
 * is something to switch to: a cast device is discoverable on the network, or playback is already
 * on a non-default output. Renders nothing otherwise, and if Cast isn't available at all (no Play
 * Services). Moving playback itself happens in PodcastPlaybackService's CastPlayer, which follows
 * the selected route.
 */
@Composable
fun DevicesButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val castContext = remember(context) { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
        ?: return
    val selector = remember(castContext) {
        castContext.mergedSelector ?: MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            )
            .build()
    }
    remember(context) { enableOutputSwitcher(context) }
    val outputs = rememberOutputs(selector)
    if (!outputs.devicesAvailable) return

    IconButton(
        modifier = modifier,
        onClick = {
            // The dialogs are AppCompat dialogs; the activity theme is an AppCompat one.
            val themed = ContextThemeWrapper(context.findActivity() ?: context, androidx.appcompat.R.style.Theme_AppCompat_Light)
            val router = MediaRouter.getInstance(context)
            // The system output switcher; false where it isn't available, then MediaRouter's own dialogs.
            if (SystemOutputSwitcherDialogController.showDialog(context)) return@IconButton
            if (router.selectedRoute.isDefaultOrBluetooth) {
                MediaRouteChooserDialog(themed).apply { routeSelector = selector }.show()
            } else {
                MediaRouteControllerDialog(themed).show()
            }
        }
    ) {
        Icon(
            imageVector = iconForRoute(outputs.deviceType, outputs.isDefaultRoute),
            contentDescription = stringResource(R.string.action_devices)
        )
    }
}

/**
 * MediaRouter only opens the system output switcher when this is on; otherwise the button falls
 * back to MediaRouter's own dialog, which does its own network discovery (blocked on Android 17
 * without ACCESS_LOCAL_NETWORK, so it shows an empty list). Transfer-to-local lets the switcher
 * move playback back to this phone. Must be set before the router is used.
 */
private fun enableOutputSwitcher(context: Context) {
    val router = MediaRouter.getInstance(context)
    router.routerParams = MediaRouterParams.Builder(router.routerParams ?: MediaRouterParams.Builder().build())
        .setOutputSwitcherEnabled(true)
        .setTransferToLocalEnabled(true)
        .build()
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The icon for the output device type MediaRouter reports for the selected route. */
internal fun iconForRoute(deviceType: Int, isDefaultRoute: Boolean): ImageVector = when {
    isDefaultRoute -> Icons.Default.PhoneAndroid
    else -> when (deviceType) {
        RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER, RouteInfo.DEVICE_TYPE_SMARTPHONE -> Icons.Default.PhoneAndroid
        RouteInfo.DEVICE_TYPE_TV, RouteInfo.DEVICE_TYPE_HDMI, RouteInfo.DEVICE_TYPE_HDMI_ARC,
        RouteInfo.DEVICE_TYPE_HDMI_EARC -> Icons.Default.Tv
        RouteInfo.DEVICE_TYPE_SPEAKER, RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER,
        RouteInfo.DEVICE_TYPE_AUDIO_VIDEO_RECEIVER, RouteInfo.DEVICE_TYPE_DOCK -> Icons.Default.Speaker
        RouteInfo.DEVICE_TYPE_GROUP -> Icons.Default.SpeakerGroup
        RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP, RouteInfo.DEVICE_TYPE_BLE_HEADSET -> Icons.Default.Bluetooth
        RouteInfo.DEVICE_TYPE_WIRED_HEADSET, RouteInfo.DEVICE_TYPE_WIRED_HEADPHONES,
        RouteInfo.DEVICE_TYPE_USB_HEADSET -> Icons.Default.Headphones
        RouteInfo.DEVICE_TYPE_HEARING_AID -> Icons.Default.Hearing
        RouteInfo.DEVICE_TYPE_CAR -> Icons.Default.DirectionsCar
        RouteInfo.DEVICE_TYPE_SMARTWATCH -> Icons.Default.Watch
        RouteInfo.DEVICE_TYPE_COMPUTER -> Icons.Default.Computer
        RouteInfo.DEVICE_TYPE_TABLET, RouteInfo.DEVICE_TYPE_TABLET_DOCKED -> Icons.Default.TabletAndroid
        RouteInfo.DEVICE_TYPE_GAME_CONSOLE -> Icons.Default.SportsEsports
        else -> Icons.Default.Devices
    }
}

private data class Outputs(val devicesAvailable: Boolean, val deviceType: Int, val isDefaultRoute: Boolean)

/**
 * The selected output and whether anything else can be picked. Asks the router for active
 * discovery while the caller is on screen (it is otherwise only passive) and follows route
 * changes. The device list is read from the router directly rather than trusting the
 * MediaRouteButton's own visibility, which stays up for system routes too.
 */
@Composable
private fun rememberOutputs(selector: MediaRouteSelector): Outputs {
    val context = LocalContext.current
    var outputs by remember { mutableStateOf(Outputs(false, RouteInfo.DEVICE_TYPE_UNKNOWN, true)) }

    DisposableEffect(selector) {
        val router = MediaRouter.getInstance(context)

        fun refresh() {
            val selected = router.selectedRoute
            outputs = Outputs(
                devicesAvailable = !selected.isDefaultOrBluetooth ||
                    router.routes.any { it.matchesSelector(selector) && !it.isDefaultOrBluetooth },
                deviceType = selected.deviceType,
                isDefaultRoute = selected.isDefault
            )
        }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, selected: RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(router: MediaRouter, unselected: RouteInfo, reason: Int) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
        onDispose { router.removeCallback(callback) }
    }
    return outputs
}
