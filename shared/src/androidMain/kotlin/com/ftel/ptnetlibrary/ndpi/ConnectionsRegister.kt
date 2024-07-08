package com.ftel.ptnetlibrary.ndpi

import android.content.Context
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import com.ftel.ptnetlibrary.ndpi.interfaces.ConnectionsListener
import com.ftel.ptnetlibrary.ndpi.models.AppStats
import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor
import com.ftel.ptnetlibrary.ndpi.models.ConnectionUpdate
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.util.*
import kotlin.math.max
import kotlin.math.min


class ConnectionsRegister {
    private val TAG = "ConnectionsRegister"

    private var mItemsRing: Array<ConnectionDescriptor?>? = null
    private lateinit var mListeners: ArrayList<ConnectionsListener>
    private lateinit var mAppsStats: SparseArray<AppStats>
    private lateinit var mGeo: Geolocation
    private var mTail = 0
    var mSize = 0
    var mCurItems = 0
    private var mUntrackedItems =
        0 // number of old connections which were discarded due to the rollover

    private var mNumMalicious = 0
    private var mNumBlocked = 0
    private var mLastFirewallBlock: Long = 0
    private var mConnsByIface: SparseIntArray? = null
    private var mAppsResolver: AppsResolver? = null

    constructor()

    constructor(ctx: Context, size: Int) {
        mTail = 0
        mCurItems = 0
        mUntrackedItems = 0
        mSize = size
        mGeo = Geolocation(ctx)
        mItemsRing = Array(mSize) { ConnectionDescriptor() }
        mListeners = ArrayList()
        mAppsStats = SparseArray() // uid -> AppStats
        mConnsByIface = SparseIntArray()
        mAppsResolver = AppsResolver(ctx)
    }

    private fun processConnectionStatus(conn: ConnectionDescriptor, stats: AppStats) {
        val isBlacklisted = conn.isBlacklisted()
        if (!conn.alerted && isBlacklisted) {
            CaptureService.requireInstance().notifyBlacklistedConnection(conn)
            conn.alerted = true
            mNumMalicious++
        } else if (conn.alerted && !isBlacklisted) {
            // the connection was whitelisted
            conn.alerted = false
            mNumMalicious--
        }
        if (!conn.blockAccounted && conn.isBlocked) {
            mNumBlocked++
            stats.numBlockedConnections++
            conn.blockAccounted = true
        } else if (conn.blockAccounted && !conn.isBlocked) {
            mNumBlocked--
            stats.numBlockedConnections--
            conn.blockAccounted = false
        }
        if (conn.isBlocked) mLastFirewallBlock = max(conn.lastSeen, mLastFirewallBlock)
    }


    @Synchronized
    fun addListener(listener: ConnectionsListener) {
        mListeners.add(listener)

        // Send the first update to sync it
        listener.connectionsChanges(mCurItems)
        Log.d(TAG, "(add) new connections listeners size: " + mListeners.size)
    }

    @Synchronized
    fun removeListener(listener: ConnectionsListener?) {
        mListeners.remove(listener)
        Log.d(TAG, "(remove) new connections listeners size: " + mListeners.size)
    }

    // called by the CaptureService in a separate thread when new connections should be added to the register
    @Synchronized
    fun newConnections(acd: Array<ConnectionDescriptor>?) {
        var conns = acd
        if (conns != null) {
            if (conns.size > mSize) {
                // this should only occur while testing with small register sizes
                // take the most recent connections
                mUntrackedItems += conns.size - mSize
                conns = Arrays.copyOfRange(conns, conns.size - mSize, conns.size)
            }
        }

        val outItems =
            (conns!!.size - min((mSize - mCurItems).toDouble(), conns.size.toDouble())).toInt()

        val insertPos = mCurItems
        var removedItems: Array<ConnectionDescriptor?>? = null

        //Log.d(TAG, "newConnections[" + mNumItems + "/" + mSize +"]: insert " + conns.length +
        //        " items at " + mTail + " (removed: " + out_items + " at " + firstPos() + ")");

        // Remove old connections
        if (outItems > 0) {
            var pos: Int = firstPos()
            removedItems = arrayOfNulls(outItems)
            for (i in 0 until outItems) {
                val conn = mItemsRing!![pos]
                if (conn != null) {
                    if (conn.ifidx > 0) {
                        var numConn = mConnsByIface!![conn.ifidx]
                        if (--numConn <= 0) mConnsByIface!!.delete(conn.ifidx) else mConnsByIface!!.put(
                            conn.ifidx,
                            numConn
                        )
                    }
                    if (conn.isBlacklisted()) mNumMalicious--
                }
                removedItems[i] = conn
                pos = (pos + 1) % mSize
            }
        }

        // Add new connections
        for (conn in conns) {
            mItemsRing!![mTail] = conn
            mTail = (mTail + 1) % mSize
            mCurItems = min((mCurItems + 1).toDouble(), mSize.toDouble()).toInt()

            // update the apps stats
            val uid = conn.uid
            val stats: AppStats = getAppsStatsOrCreate(uid)
            if (conn.ifidx > 0) {
                val numConn = mConnsByIface!![conn.ifidx]
                mConnsByIface!!.put(conn.ifidx, numConn + 1)
            }

            // Geolocation
            val dstAddr = conn.getDstAddr()
            conn.country = mGeo.getCountryCode(dstAddr)
            conn.asn = mGeo.getASN(dstAddr)
            //Log.d(TAG, "IP geolocation: IP=" + conn.dst_ip + " -> country=" + conn.country + ", ASN: " + conn.asn);
            val app = mAppsResolver!!.getAppByUid(conn.uid, 0)
            if (app != null) conn.encryptedPayload = Utils.hasEncryptedPayload(app, conn)
            processConnectionStatus(conn, stats)
            stats.numConnections++
            stats.rcvdBytes += conn.rcvdBytes
            stats.sentBytes += conn.sentBytes
        }


        mUntrackedItems += outItems
        mListeners.forEach { listener ->
            if (outItems > 0) {
                if (removedItems != null) {
                    listener.connectionsRemoved(0, removedItems.filterNotNull().toTypedArray())
                }
            }

            if (conns.isNotEmpty()) {
                listener.connectionsAdded(insertPos - outItems, conns)
            }
        }
    }

    // called by the CaptureService in a separate thread when connections should be updated
    @Synchronized
    fun connectionsUpdates(acu: Array<ConnectionUpdate?>?) {
        if (mCurItems == 0) return

        val first = firstPos()
        val last = lastPos()
        val firstId: Int = mItemsRing!![first]!!.incrId
        val lastId: Int = mItemsRing!![last]!!.incrId
        var changedPos = IntArray(acu!!.size)
        var k = 0

        Log.d(
            TAG,
            "connectionsUpdates: items=$mCurItems, firstId=$firstId, lastId=$lastId"
        )

        for (update in acu) {
            val id: Int = update!!.incrId

            // ignore updates for untracked items
            if (id in firstId..lastId) {
                val pos = (id - firstId + first) % mSize
                val conn = mItemsRing!![pos]
                assert(conn!!.incrId == id)

                // update the app stats
                val stats: AppStats = getAppsStatsOrCreate(conn.uid)
                stats.sentBytes += update.sentBytes - conn.sentBytes
                stats.rcvdBytes += update.rcvdBytes - conn.rcvdBytes

                //Log.d(TAG, "update " + update.incr_id + " -> " + update.update_type);
                conn.processUpdate(update)
                processConnectionStatus(conn, stats)
                changedPos[k++] = (pos + mSize - first) % mSize
            }
        }

        if (k == 0) // no updates for items in the ring
            return

        if (k != acu.size) // some untracked items where skipped, shrink the array
            changedPos = changedPos.copyOf(k)

        for (listener in mListeners) listener.connectionsUpdated(changedPos)
    }

    // returns the position in mItemsRing of the oldest connection
    @Synchronized
    private fun firstPos(): Int {
        return if (mCurItems < mSize) 0 else mTail
    }

    // returns the position in mItemsRing of the newest connection
    @Synchronized
    private fun lastPos(): Int {
        return (mTail - 1 + mSize) % mSize
    }

    @Synchronized
    private fun getAppsStatsOrCreate(uid: Int): AppStats {
        var stats = mAppsStats[uid]
        if (stats == null) {
            stats = AppStats(uid)
            mAppsStats.put(uid, stats)
        }
        return stats
    }

    @Synchronized
    fun getAppsStats(): List<AppStats> {
        val rv = ArrayList<AppStats>(mAppsStats.size())
        for (i in 0 until mAppsStats.size()) {
            // Create a clone to avoid concurrency issues
            val stats = mAppsStats.valueAt(i).clone()
            rv.add(stats)
        }
        return rv
    }

    // get the i-th oldest connection
    @Synchronized
    fun getConn(i: Int): ConnectionDescriptor? {
        if (i < 0 || i >= mCurItems) return null
        val pos = (firstPos() + i) % mSize
        return mItemsRing!![pos]
    }

    @Synchronized
    fun getConnPositionById(id: Int): Int {
        if (mCurItems <= 0) return -1

        val first = mItemsRing!![firstPos()]!!
        val last = mItemsRing!![lastPos()]!!

        if ((id < first.incrId) || (id > last.incrId)) return -1

        return (id - first.incrId)
    }

    @Synchronized
    fun getConnById(id: Int): ConnectionDescriptor? {
        val pos: Int = getConnPositionById(id)
        if (pos < 0) return null

        return getConn(pos)
    }

    @Synchronized
    fun getAppStats(uid: Int): AppStats {
        return mAppsStats[uid]
    }

    @Synchronized
    fun releasePayloadMemory() {
        Log.i(TAG, "releaseFullPayloadMemory called")
        for (i in 0 until mCurItems) {
            val conn = mItemsRing!![i]!!
            conn.dropPayload()
        }
    }


    // Getter

    fun getAppResolver(): AppsResolver? {
        return mAppsResolver
    }
}