package com.ftel.ptnetlibrary.ndpi.models

import android.content.Context
import android.os.SystemClock
import android.util.ArrayMap
import android.util.Log
import com.ftel.ptnetlibrary.ndpi.services.CaptureService


class Blocklist(ctx: Context) : MatchList(ctx, Prefs.PREF_BLOCKLIST) {
    companion object {
        private const val TAG = "Blocklist"
    }

    // access to mUidToGrace must be synchronized as it can happen either from the UI thread or from
    // the CaptureService.connUpdateWork thread
    private val mUidToGrace: ArrayMap<Int, Long> = ArrayMap()

    @Synchronized
    fun unblockAppForMinutes(uid: Int, minutes: Int): Boolean {
        val oldVal = mUidToGrace.put(uid, SystemClock.elapsedRealtime() + minutes * 60_000L)
        Log.i(TAG, "Grace app: $uid for $minutes minutes (old: $oldVal)")
        return oldVal == null
    }

    @Synchronized
    fun checkGracePeriods(): Boolean {
        val now = SystemClock.elapsedRealtime()
        var changed = false
        val iter = mUidToGrace.entries.iterator()

        while (iter.hasNext()) {
            val entry = iter.next()
            if (now >= entry.value) {
                Log.i(TAG, "Grace period ended for app: ${entry.key}")
                iter.remove()
                changed = true
            }
        }

        return changed
    }

    @Synchronized
    override fun isExemptedApp(uid: Int): Boolean {
        return mUidToGrace.containsKey(uid)
    }

    override fun matchesApp(uid: Int): Boolean {
        if (!super.matchesApp(uid)) return false
        synchronized(this) {
            return !isExemptedApp(uid)
        }
    }

    @Synchronized
    override fun removeApp(uid: Int) {
        mUidToGrace.remove(uid)
        super.removeApp(uid)
    }

    @Synchronized
    override fun addApp(uid: Int): Boolean {
        mUidToGrace.remove(uid)
        return super.addApp(uid)
    }

    fun saveAndReload() {
        save()
        if (CaptureService.isServiceActive()) CaptureService.requireInstance().reloadBlocklist()
    }
}