package com.ftel.ptnetlibrary.ndpi.models

import android.view.LayoutInflater
import com.ftel.ptnetlibrary.ndpi.PCAPdroid
import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor.Status
import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor.DecryptionStatus
import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor.FilteringStatus
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.Serializable
import java.util.Locale
import com.ftel.ptnetlibrary.R


class FilterDescriptor() : Serializable {
    var status: Status? = null
    var showMasked = false
    var onlyBlacklisted = false
    var onlyCleartext = false
    var filteringStatus: FilteringStatus? = null
    var decStatus: DecryptionStatus? = null
    var iface: String? = null
    var uid = -2 // this is persistent and used internally (AppDetailsActivity)


    init {
        clear()
        assert(!isSet())
    }

    fun isSet(): Boolean {
        return (status !== Status.STATUS_INVALID || decStatus != DecryptionStatus.INVALID || filteringStatus != FilteringStatus.INVALID || iface != null
                || onlyBlacklisted
                || onlyCleartext || uid != -2) || !showMasked && !PCAPdroid().getInstance()
            .getVisualizationMask().isEmpty()
    }

    fun matches(conn: ConnectionDescriptor): Boolean {
        return ((showMasked || !PCAPdroid().getInstance().getVisualizationMask().matches(conn))
                && (!onlyBlacklisted || conn.isBlacklisted())
                && (!onlyCleartext || conn.isCleartext())
                && (status == Status.STATUS_INVALID || conn.getStatus() == status)
                && (decStatus == DecryptionStatus.INVALID || conn.getDecryptionStatus() == decStatus)
                && (filteringStatus == FilteringStatus.INVALID || filteringStatus == FilteringStatus.BLOCKED == conn.isBlocked)
                && (iface == null || CaptureService().getInterfaceName(conn.ifidx) == iface)
                && (uid == -2 || uid == conn.uid))
    }

    fun clear() {
        showMasked = true
        onlyBlacklisted = false
        onlyCleartext = false
        status = Status.STATUS_INVALID
        decStatus = DecryptionStatus.INVALID
        filteringStatus = FilteringStatus.INVALID
        iface = null
    }
}