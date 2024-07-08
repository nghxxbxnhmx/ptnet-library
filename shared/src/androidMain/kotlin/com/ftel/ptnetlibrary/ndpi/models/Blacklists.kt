package com.ftel.ptnetlibrary.ndpi.models

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.ArrayMap
import android.util.Log
import androidx.collection.ArraySet
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.Utils
import com.ftel.ptnetlibrary.ndpi.interfaces.BlacklistsStateListener
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.io.File
import java.lang.reflect.Type


class Blacklists {
    val PREF_BLACKLISTS_STATUS = "blacklists_status"
    val BLACKLISTS_UPDATE_MILLIS = (86400 * 1000 // 1d
            ).toLong()
    private val TAG = "Blacklists"
    private val mLists: ArrayList<BlacklistDescriptor> = ArrayList<BlacklistDescriptor>()
    private val mListByFname: ArrayMap<String, BlacklistDescriptor> = ArrayMap()
    private val mListeners: ArrayList<BlacklistsStateListener> =
        ArrayList<BlacklistsStateListener>()
    private var mPrefs: SharedPreferences? = null
    private var mContext: Context? = null
    private var mUpdateInProgress = false
    private var mStopRequest = false
    private var mLastUpdate: Long = 0
    private var mLastUpdateMonotonic: Long = 0
    private var mNumDomainRules = 0
    private var mNumIPRules = 0

    constructor(ctx: Context) {
        mLastUpdate = 0;
        mLastUpdateMonotonic = -BLACKLISTS_UPDATE_MILLIS;
        mNumDomainRules = 0;
        mNumIPRules = 0;
        mContext = ctx;
        mUpdateInProgress = false;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);

        // Domains
        addList(
            "Maltrail", BlacklistDescriptor.Type.DOMAIN_BLACKLIST, "maltrail-malware-domains.txt",
            "https://raw.githubusercontent.com/stamparm/aux/master/maltrail-malware-domains.txt"
        );

        // IPs
        addList(
            "Emerging Threats", BlacklistDescriptor.Type.IP_BLACKLIST, "emerging-Block-IPs.txt",
            "https://rules.emergingthreats.net/fwrules/emerging-Block-IPs.txt"
        );
        addList(
            "SSLBL Botnet C2", BlacklistDescriptor.Type.IP_BLACKLIST, "abuse_sslipblacklist.txt",
            "https://sslbl.abuse.ch/blacklist/sslipblacklist.txt"
        );
        addList(
            "Feodo Tracker Botnet C2",
            BlacklistDescriptor.Type.IP_BLACKLIST,
            "feodotracker_ipblocklist.txt",
            "https://feodotracker.abuse.ch/downloads/ipblocklist.txt"
        ); // NOTE: some IPs are in emergingthreats, but not all
        addList(
            "DigitalSide Threat-Intel",
            BlacklistDescriptor.Type.IP_BLACKLIST,
            "digitalsideit_ips.txt",
            "https://raw.githubusercontent.com/davidonzo/Threat-Intel/master/lists/latestips.txt"
        );

        // To review
        //https://github.com/StevenBlack/hosts
        //https://phishing.army/download/phishing_army_blocklist.txt
        //https://snort.org/downloads/ip-block-list

        deserialize();
        checkFiles();
    }

    private fun addList(label: String, tp: BlacklistDescriptor.Type, fname: String, url: String) {
        val item = BlacklistDescriptor(label, tp, fname, url)
        mLists.add(item)
        mListByFname[fname] = item
    }

    fun deserialize() {
        val serialized = mPrefs!!.getString(PREF_BLACKLISTS_STATUS, "")
        if (serialized!!.isNotEmpty()) {
            val obj = JsonParser.parseString(serialized).getAsJsonObject()
            mLastUpdate = obj.getAsJsonPrimitive("last_update").asLong
            mNumDomainRules = obj.getAsJsonPrimitive("num_domain_rules").asInt
            mNumIPRules = obj.getAsJsonPrimitive("num_ip_rules").asInt

            // set the monotonic time based on the last update wall clock time
            val millisSinceLastUpdate = System.currentTimeMillis() - mLastUpdate
            if (millisSinceLastUpdate > 0) mLastUpdateMonotonic =
                SystemClock.elapsedRealtime() - millisSinceLastUpdate
            val blacklistsObj = obj.getAsJsonObject("blacklists")
            if (blacklistsObj != null) { // support old format
                for ((key, value) in blacklistsObj.entrySet()) {
                    val bl = mListByFname[key]
                    if (bl != null) {
                        val blObj = value.getAsJsonObject()
                        bl.num_rules = blObj.getAsJsonPrimitive("num_rules").asInt
                        bl.setUpdated(blObj.getAsJsonPrimitive("last_update").asLong)
                    }
                }
            }
        }
    }

    class Serializer : JsonSerializer<Blacklists> {
        override fun serialize(
            src: Blacklists,
            typeOfSrc: Type,
            context: JsonSerializationContext?
        ): JsonElement {
            val blacklistsObj = JsonObject()

            for (bl in src.mLists) {
                val blObj = JsonObject()
                blObj.add("num_rules", JsonPrimitive(bl.num_rules))
                blObj.add("last_update", JsonPrimitive(bl.getLastUpdate()))
                blacklistsObj.add(bl.fname, blObj)
            }

            val rv = JsonObject()
            rv.add("last_update", JsonPrimitive(src.mLastUpdate))
            rv.add("num_domain_rules", JsonPrimitive(src.mNumDomainRules))
            rv.add("num_ip_rules", JsonPrimitive(src.mNumIPRules))
            rv.add("blacklists", blacklistsObj)

            return rv
        }
    }

    private fun toJson(): String {
        val gson = GsonBuilder().registerTypeAdapter(javaClass, Serializer())
            .create()
        return gson.toJson(this)
    }

    fun save() {
        mPrefs!!.edit()
            .putString(PREF_BLACKLISTS_STATUS, toJson())
            .apply()
    }

    private fun getListPath(bl: BlacklistDescriptor): String {
        return mContext!!.filesDir.path + "/malware_bl/" + bl.fname
    }

    private fun checkFiles() {
        val validLists: ArraySet<File> = ArraySet()

        // Ensure that all the lists files exist, otherwise force update
        for (bl in mLists) {
            val f = File(getListPath(bl))
            validLists.add(f)
            if (!f.exists()) {
                // must update
                mLastUpdateMonotonic = -BLACKLISTS_UPDATE_MILLIS
            }
        }

        // Ensure that the only the specified lists exist
        val bldir = File(mContext!!.filesDir.path + "/malware_bl")
        bldir.mkdir()
        val files = bldir.listFiles()
        if (files != null) {
            for (f in files) {
                if (!validLists.contains(f)) {
                    Log.i(TAG, "Removing unknown list: " + f.path)
                    f.delete()
                }
            }
        }
    }

    fun needsUpdate(firstUpdate: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        return now - mLastUpdateMonotonic >= BLACKLISTS_UPDATE_MILLIS || firstUpdate && getNumUpdatedBlacklists() < getNumBlacklists()
    }

    // NOTE: invoked in a separate thread (CaptureService.mBlacklistsUpdateThread)
    fun update() {
        mUpdateInProgress = true
        mStopRequest = false
        for (bl in mLists) bl.setUpdating()
        notifyListeners()
        Log.i(TAG, "Updating " + mLists.size + " blacklists...")
        for (bl in mLists) {
            if (mStopRequest) {
                Log.i(TAG, "Stop request received, abort")
                break
            }
            Log.i(TAG, "\tupdating " + bl.fname + "...")
            if (Utils.downloadFile(
                    bl.url,
                    getListPath(bl)
                )
            ) bl.setUpdated(System.currentTimeMillis()) else bl.setOutdated()
            notifyListeners()
        }
        mLastUpdate = System.currentTimeMillis()
        mLastUpdateMonotonic = SystemClock.elapsedRealtime()
        notifyListeners()
    }


    class NativeBlacklistStatus(val fName: String, val numRules: Int)

    // Called when the blacklists are loaded in memory by the native code
    fun onNativeLoaded(loadedBlacklists: Array<NativeBlacklistStatus?>) {
        var numLoaded = 0
        var numDomains = 0
        var numIps = 0
        val loaded = ArraySet<String>()
        for (blStatus in loadedBlacklists) {
            if (blStatus == null) break
            val bl = mListByFname[blStatus.fName]
            if (bl != null) {
                // Update the number of rules
                bl.num_rules = blStatus.numRules
                bl.loaded = true
                loaded.add(bl.fname)
                if (bl.type == BlacklistDescriptor.Type.DOMAIN_BLACKLIST) numDomains += blStatus.numRules else numIps += blStatus.numRules
                numLoaded++
            } else Log.w(TAG, "Loaded unknown blacklist " + blStatus.fName)
        }
        for (bl in mLists) {
            if (!loaded.contains(bl.fname)) {
                Log.w(TAG, "Blacklist not loaded: " + bl.fname)
                bl.loaded = false
            }
        }
        Log.i(
            TAG,
            "Blacklists loaded: $numLoaded lists, $numDomains domains, $numIps IPs"
        )
        mNumDomainRules = numDomains
        mNumIPRules = numIps
        mUpdateInProgress = false
        notifyListeners()
    }

    fun iter(): Iterator<BlacklistDescriptor> {
        return mLists.iterator()
    }

    fun getNumLoadedDomainRules(): Int {
        return mNumDomainRules
    }

    fun getNumLoadedIPRules(): Int {
        return mNumIPRules
    }

    fun getLastUpdate(): Long {
        return mLastUpdate
    }

    fun getNumBlacklists(): Int {
        return mLists.size
    }

    private fun getNumUpdatedBlacklists(): Int {
        var ctr = 0
        for (bl in mLists) {
            if (bl.isUpToDate()) ctr++
        }
        return ctr
    }

    private fun notifyListeners() {
        for (listener in mListeners) listener.onBlacklistsStateChanged()
    }

    fun addOnChangeListener(listener: BlacklistsStateListener?) {
        mListeners.add(listener!!)
    }

    fun removeOnChangeListener(listener: BlacklistsStateListener?) {
        mListeners.remove(listener)
    }

    fun isUpdateInProgress(): Boolean {
        return mUpdateInProgress
    }

    fun abortUpdate() {
        mStopRequest = true
    }
}