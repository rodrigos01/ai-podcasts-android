package com.rodrigos01.aipodcasts.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "PositionSync"
private const val KEY_POSITION_MS = "positionMs"
private const val KEY_UPDATED_AT = "updatedAt"

/**
 * Data Layer glue for [SyncedPositionStore]: one DataItem per episode at
 * `/aipodcasts/position/<episodeId>`. Items persist and sync whenever the phone and watch are
 * connected, so a position saved while apart reaches the other device on reconnect.
 */
object PositionSync {
    const val PATH_PREFIX = "/aipodcasts/position/"

    /**
     * Call once from `Application.onCreate`: listens for the other device's updates while the
     * process is alive (the manifest [PositionSyncListenerService] covers the rest) and pulls
     * whatever has synced since this app last ran.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        val store = SyncedPositionStore.get(appContext)
        val dataClient = Wearable.getDataClient(appContext)
        dataClient.addListener { events -> handle(store, events) }
        dataClient.getDataItems(Uri.Builder().scheme("wear").path(PATH_PREFIX).build(), DataClient.FILTER_PREFIX)
            .addOnSuccessListener { items ->
                try {
                    items.forEach { apply(store, it.uri.path, DataMapItem.fromDataItem(it).dataMap) }
                } finally {
                    items.release()
                }
            }
            .addOnFailureListener { Log.w(TAG, "Initial position sync failed", it) }
    }

    internal fun handle(store: SyncedPositionStore, events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            apply(store, event.dataItem.uri.path, DataMapItem.fromDataItem(event.dataItem).dataMap)
        }
    }

    private fun apply(store: SyncedPositionStore, path: String?, data: com.google.android.gms.wearable.DataMap) {
        if (path == null || !path.startsWith(PATH_PREFIX)) return
        store.applyRemote(
            episodeId = path.removePrefix(PATH_PREFIX),
            positionMs = data.getLong(KEY_POSITION_MS),
            updatedAt = data.getLong(KEY_UPDATED_AT)
        )
    }
}

internal class DataLayerPositionPublisher(context: Context) : PositionPublisher {
    private val appContext = context.applicationContext ?: context

    override fun publish(episodeId: String, positionMs: Long, updatedAt: Long, urgent: Boolean) {
        runCatching {
            val request = PutDataMapRequest.create(PositionSync.PATH_PREFIX + episodeId).apply {
                dataMap.putLong(KEY_POSITION_MS, positionMs)
                dataMap.putLong(KEY_UPDATED_AT, updatedAt)
            }.asPutDataRequest().also { if (urgent) it.setUrgent() }
            // Fails (harmlessly) on devices without the Wearable API; nothing to sync with then.
            Wearable.getDataClient(appContext).putDataItem(request)
                .addOnFailureListener { Log.d(TAG, "Position not published: ${it.message}") }
        }
    }
}

class PositionSyncListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        PositionSync.handle(SyncedPositionStore.get(this), dataEvents)
    }
}
