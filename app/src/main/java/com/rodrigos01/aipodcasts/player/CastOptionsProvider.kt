package com.rodrigos01.aipodcasts.player

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.rodrigos01.aipodcasts.R

/**
 * Cast configuration, referenced from the manifest. The receiver app is `cast_receiver_app_id`
 * (res/values/cast.xml): the stock Default Media Receiver unless a Styled Media Receiver
 * registered in the Google Cast console is configured there, which is what gives the TV the
 * app's name and colors.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(context.getString(R.string.cast_receiver_app_id))
            // Same as Media3's DefaultCastOptionsProvider. Remote-to-local is what lets the
            // system output switcher move playback from a cast device back to this phone.
            .setRemoteToLocalEnabled(true)
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
