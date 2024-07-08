package com.ftel.ptnetlibrary.ndpi.models

import com.ftel.ptnetlibrary.R

import android.content.Context

import androidx.core.content.ContextCompat

class BlacklistDescriptor {
    var label: String
    var type: Type // NOTE: used via JNI
    var fname: String // NOTE: used via JNI
    var url: String

    var mLastUpdate: Long = 0
    var mUpToDate = false
    var mUpdating = false
    var loaded = false
    var num_rules = 0

    // NOTE: used via JNI
    enum class Type {
        IP_BLACKLIST,
        DOMAIN_BLACKLIST
    }


    enum class Status {
        NOT_LOADED,
        OUTDATED,
        UPDATING,
        UP_TO_DATE
    }


    constructor(label: String, type: Type, fname: String, url: String) {
        this.label = label
        this.type = type
        this.fname = fname
        this.url = url
    }

    fun setUpdating() {
        mUpdating = true
        mUpToDate = false
    }

    fun setOutdated() {
        mUpdating = false
        mUpToDate = false
    }

    fun setUpdated(now: Long) {
        mUpdating = false
        mLastUpdate = now
        mUpToDate = mLastUpdate != 0L
    }

    fun getLastUpdate(): Long {
        return mLastUpdate
    }

    fun isUpToDate(): Boolean {
        return mUpToDate
    }

    private fun getStatus(): Status {
        if (mUpdating) return Status.UPDATING
        if (!loaded) return Status.NOT_LOADED
        return if (!mUpToDate) Status.OUTDATED else Status.UP_TO_DATE
    }

    fun getStatusLabel(ctx: Context): String {
        var id = -1
        id = when (getStatus()) {
            Status.NOT_LOADED -> R.string.status_not_loaded
            Status.OUTDATED -> R.string.status_outdated
            Status.UPDATING -> R.string.status_updating
            Status.UP_TO_DATE -> R.string.status_uptodate
        }
        return ctx.getString(id)
    }

    fun getStatusColor(ctx: Context?): Int {
        var id = -1
        id = when (getStatus()) {
            Status.NOT_LOADED -> R.color.danger
            Status.OUTDATED -> R.color.warning
            Status.UPDATING -> R.color.in_progress
            Status.UP_TO_DATE -> R.color.ok
        }
        return ContextCompat.getColor(ctx!!, id)
    }

    fun getTypeLabel(ctx: Context): String {
        val id: Int =
            if (type == Type.IP_BLACKLIST) R.string.blacklist_type_ip else R.string.blacklist_type_domain
        return ctx.getString(id)
    }
}