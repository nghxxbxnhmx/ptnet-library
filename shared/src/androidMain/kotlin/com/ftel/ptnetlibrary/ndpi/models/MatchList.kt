package com.ftel.ptnetlibrary.ndpi.models

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.text.style.StyleSpan
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.AppsResolver
import com.ftel.ptnetlibrary.ndpi.Utils
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

import com.ftel.ptnetlibrary.R
@SuppressLint("NewApi")
open class MatchList {
    private val TAG = "MatchList"
    private val italic = StyleSpan(Typeface.ITALIC)
    private var mContext: Context? = null
    private var mPrefs: SharedPreferences? = null
    private var mPrefName: String? = null
    private val mListeners = ArrayList<ListChangeListener>()
    private val mRules = ArrayList<Rule>()
    private val mMatches = ArrayMap<String, Rule>()
    private val mUids = ArraySet<Int>()
    private val mPackageToUid = ArrayMap<String, Int>()
    private var mResolver: AppsResolver? = null
    private var mMigration = false

    constructor()

    constructor(ctx: Context, prefName: String) {
        mContext = ctx;
        mPrefName = prefName; // The preference to bake the list rules
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        mResolver = AppsResolver(ctx);
        reload();
    }

    enum class RuleType {
        @SerializedName("APP")
        APP,

        @SerializedName("IP")
        IP,

        @SerializedName("HOST")
        HOST,

        @SerializedName("PROTOCOL")
        PROTOCOL,

        @SerializedName("COUNTRY")
        COUNTRY
    }

    inner class Rule internal constructor(val tp: RuleType, val value: Any) {
        val label: String
            get() = getRuleLabel(mContext!!, tp, value.toString())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Rule) return false

            return tp == other.tp && value == other.value
        }

        override fun hashCode(): Int {
            var result = tp.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }
    }

    interface ListChangeListener {
        fun onListChanged()
    }

    class ListDescriptor {
        val apps: MutableList<String> = ArrayList()
        val hosts: MutableList<String> = ArrayList()
        val ips: MutableList<String> = ArrayList()
    }

    init {
        reload()
    }

    private fun reload() {
        val serialized = mPrefs!!.getString(mPrefName, "") ?: ""

        if (serialized.isNotEmpty()) {
            fromJson(serialized)

            if (mMigration) {
                Log.i(TAG, "Migration completed")
                save()
                mMigration = false
            }
        } else {
            clear()
        }
    }

    fun save() {
        mPrefs!!.edit().putString(mPrefName, toJson(false)).apply()
    }

    fun getRuleLabel(ctx: Context, tp: RuleType, value: String): String {
        val resid = when (tp) {
            RuleType.APP -> R.string.app_val
            RuleType.IP -> R.string.ip_address_val
            RuleType.HOST -> R.string.host_val
            RuleType.PROTOCOL -> R.string.protocol_val
            RuleType.COUNTRY -> R.string.country_val
        }

        return when (tp) {
            RuleType.APP -> {
                val app = AppsResolver.resolveInstalledApp(ctx.packageManager, value, 0, false)
                app?.getName() ?: value
            }

            RuleType.HOST -> Utils.cleanDomain(value)
            RuleType.COUNTRY -> Utils.getCountryName(ctx, value)
            else -> ctx.getString(resid)
        }
    }

    @JsonAdapter(Serializer::class)
    private class Serializer : JsonSerializer<MatchList> {
        override fun serialize(
            src: MatchList,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val result = JsonObject()
            val rulesArr = JsonArray()

            src.mRules.forEach { rule ->
                val ruleObject = JsonObject()
                ruleObject.add("type", JsonPrimitive(rule.tp.name))
                ruleObject.add("value", JsonPrimitive(rule.value.toString()))
                rulesArr.add(ruleObject)
            }

            result.add("rules", rulesArr)
            return result
        }
    }

    private fun deserialize(obj: JsonObject, maxRules: Int): Int {
        var numRules = 0

        try {
            val ruleArray = obj.getAsJsonArray("rules") ?: return -1
            clear(false)

            ruleArray.forEach { el ->
                val ruleObj = el.asJsonObject
                val typeStr = ruleObj.get("type").asString
                var value = ruleObj.get("value").asString
                val type = try {
                    RuleType.valueOf(typeStr)
                } catch (e: IllegalArgumentException) {
                    if (typeStr == "ROOT_DOMAIN") {
                        Log.i(TAG, String.format("ROOT_DOMAIN %s migrated", value))
                        RuleType.HOST
                    } else {
                        e.printStackTrace()
                        return@forEach
                    }
                }

                if (type == RuleType.APP) {
                    try {
                        val uid = value.toInt()
                        val app = mResolver!!.getAppByUid(uid, 0)
                        if (app != null) {
                            value = app.getPackageName()
                            Log.i(TAG, String.format("UID %d resolved to package %s", uid, value))
                            mMigration = true
                        } else {
                            Log.w(TAG, "Ignoring unknown UID $uid")
                            return@forEach
                        }
                    } catch (ignored: NumberFormatException) {
                    }

                    val app = mResolver!!.getAppByPackage(value, 0)
                    if (app != null && app.getPackageName() != value) {
                        Log.i(
                            TAG,
                            "The UID ${app.getUid()} mapping has changed from $value to ${app.getPackageName()}"
                        )
                        value = app.getPackageName()
                        mMigration = true
                    }
                }

                if (addRule(Rule(type, value), false)) {
                    numRules++

                    if (maxRules in 1..numRules) {
                        return numRules
                    }
                }
            }

            notifyListeners()
        } catch (e: Exception) {
            e.printStackTrace()
            return -1
        }

        return numRules
    }

    fun addIp(ip: String): Boolean = addRule(Rule(RuleType.IP, ip))
    fun addHost(info: String): Boolean = addRule(Rule(RuleType.HOST, Utils.cleanDomain(info)))
    fun addProto(proto: String): Boolean = addRule(Rule(RuleType.PROTOCOL, proto))
    fun addCountry(countryCode: String): Boolean = addRule(Rule(RuleType.COUNTRY, countryCode))
    fun addApp(pkg: String): Boolean = addRule(Rule(RuleType.APP, pkg))
    open fun addApp(uid: Int): Boolean {
        val app = mResolver!!.getAppByUid(uid, 0)
        return app?.let { addApp(it.getPackageName()) } ?: run {
            Log.e(TAG, "could not resolve UID $uid")
            false
        }
    }

    fun removeIp(ip: String) = removeRule(Rule(RuleType.IP, ip))
    fun removeHost(info: String) = removeRule(Rule(RuleType.HOST, Utils.cleanDomain(info)))
    fun removeProto(proto: String) = removeRule(Rule(RuleType.PROTOCOL, proto))
    fun removeCountry(countryCode: String) = removeRule(Rule(RuleType.COUNTRY, countryCode))
    private fun removeApp(pkg: String) = removeRule(Rule(RuleType.APP, pkg))
    open fun removeApp(uid: Int) {
        val app = mResolver!!.getAppByUid(uid, 0)
        if (app == null) {
            Log.e(TAG, "could not resolve UID $uid")
        } else {
            removeApp(app.getPackageName())
        }
    }

    private fun matchKey(tp: RuleType, `val`: Any): String = "$tp@$`val`"

    private fun addRule(rule: Rule, notify: Boolean = true): Boolean {
        val value = rule.value.toString()
        val key = matchKey(rule.tp, value)

        if (mMatches.containsKey(key)) {
            return false
        }

        if (rule.tp == RuleType.APP) {
            val uid = mResolver!!.getUid(value)
            if (uid == Utils.UID_NO_FILTER) {
                return false
            }

            mPackageToUid[value] = uid
            mUids.add(uid)
        }

        mRules.add(rule)
        mMatches[key] = rule
        if (notify) {
            notifyListeners()
        }
        return true
    }

    private fun addRule(rule: Rule): Boolean {
        return addRule(rule, true)
    }

    fun addRules(toAdd: MatchList): Int {
        var num_added = 0
        val it: Iterator<Rule> = toAdd.iterRules()
        while (it.hasNext()) {
            val rule = it.next()
            if (addRule(rule, false)) num_added++
        }
        if (num_added > 0) notifyListeners()
        return num_added
    }

    private fun removeRule(rule: Rule): Boolean {
        val value = rule.value.toString()
        val key = matchKey(rule.tp, value)
        val removed = mMatches.remove(key) != null

        if (removed) {
            mRules.remove(rule)
            if (rule.tp == RuleType.APP) {
                mPackageToUid.remove(value)
                mUids.remove(mResolver!!.getUid(value))
            }

            notifyListeners()
        }

        return removed
    }

    private fun iterRules(): Iterator<Rule> {
        return mRules.iterator()
    }

    // Match checking -------------------------------------------------------------------------START
    open fun matchesApp(uid: Int): Boolean = mUids.contains(uid)
    private fun matchesIp(ip: String): Boolean = mMatches.containsKey(matchKey(RuleType.IP, ip))
    private fun matchesProto(l7proto: String): Boolean =
        mMatches.containsKey(matchKey(RuleType.PROTOCOL, l7proto))

    private fun matchesExactHost(host: String): Boolean {
        val cleanedHost = Utils.cleanDomain(host)
        return mMatches.containsKey(matchKey(RuleType.HOST, cleanedHost))
    }

    private fun matchesHost(host: String): Boolean {
        val cleanedHost = Utils.cleanDomain(host)
        if (matchesExactHost(cleanedHost)) {
            return true
        }

        val domain = Utils.getSecondLevelDomain(cleanedHost)
        return domain != host && mMatches.containsKey(matchKey(RuleType.HOST, domain))
    }

    private fun matchesCountry(countryCode: String): Boolean =
        mMatches.containsKey(matchKey(RuleType.COUNTRY, countryCode))

    fun matches(conn: ConnectionDescriptor): Boolean {
        if (mMatches.isEmpty()) return false
        val hasInfo = conn.info != null && conn.info!!.isNotEmpty()
        return matchesApp(conn.uid) ||
                matchesIp(conn.dstIp) ||
                matchesProto(conn.l7proto) ||
                matchesCountry(conn.country) || hasInfo && matchesHost(conn.info!!)
    }
    // Match checking ---------------------------------------------------------------------------END

    // Clear data -----------------------------------------------------------------------------START
    fun clear(notify: Boolean) {
        val hasRules = mRules.size > 0
        mRules.clear()
        mMatches.clear()
        mPackageToUid.clear()
        mUids.clear()
        if (notify && hasRules) notifyListeners()
    }

    fun clear() {
        clear(true)
    }
    // Clear data -------------------------------------------------------------------------------END

    fun isEmpty(): Boolean {
        return mRules.size == 0
    }

    fun getSize(): Int {
        return mRules.size
    }

    private fun toJson(prettyPrint: Boolean): String {
        val builder =
            GsonBuilder().registerTypeAdapter(javaClass, Serializer())
        if (prettyPrint) builder.setPrettyPrinting()
        val gson = builder.create()
        //Log.d(TAG, "toJson: " + serialized);
        return gson.toJson(this)
    }

    private fun fromJson(jsonStr: String, maxRules: Int): Int {
        return try {
            val el = JsonParser.parseString(jsonStr)
            if (!el.isJsonObject) -1 else deserialize(el.getAsJsonObject(), maxRules)
        } catch (e: JsonSyntaxException) {
            e.printStackTrace()
            -1
        }
    }

    private fun fromJson(jsonStr: String): Int {
        return fromJson(jsonStr, -1)
    }

    // can be used by a subclass to exempt specific app (e.g. Blocklist grace apps)
    open fun isExemptedApp(uid: Int): Boolean {
        return false
    }

    fun toListDescriptor(): ListDescriptor {
        val desc = ListDescriptor()
        mRules.forEach { rule ->
            val tp = rule.tp
            val `val` = rule.value.toString()

            when (tp) {
                RuleType.APP -> desc.apps.add(`val`)
                RuleType.IP -> desc.ips.add(`val`)
                RuleType.HOST -> desc.hosts.add(`val`)
                else -> {
                }
            }
        }

        return desc
    }

    fun addListChangeListener(listener: ListChangeListener) = mListeners.add(listener)
    fun removeListChangeListener(listener: ListChangeListener) = mListeners.remove(listener)
    private fun notifyListeners() = mListeners.forEach { it.onListChanged() }
    fun uidMappingChanged(pkg: String, uid: Int) {
        if (mPackageToUid[pkg] != uid) {
            mPackageToUid[pkg] = uid
            notifyListeners()
        }
    }

    fun uidMappingChanged(pkg: String): Boolean {
        if (!mMatches.containsKey(matchKey(RuleType.APP, pkg))) return false
        var changed = false
        var oldUid = mPackageToUid[pkg]
        val app = mResolver!!.getAppByPackage(pkg, 0)
        if (oldUid != null && (app == null || app.getUid() != oldUid)) {
            Log.i(TAG, "Remove old UID mapping of $pkg: $oldUid")
            mPackageToUid.remove(pkg)
            mUids.remove(oldUid)
            changed = true
            oldUid = null // possibly add the new UID mapping below
        }
        if (oldUid == null && app != null) {
            val newUid = app.getUid()
            Log.i(TAG, "Add UID mapping of $pkg: $newUid")
            mPackageToUid[pkg] = newUid
            mUids.add(newUid)
            changed = true
        }
        return changed
    }
}
