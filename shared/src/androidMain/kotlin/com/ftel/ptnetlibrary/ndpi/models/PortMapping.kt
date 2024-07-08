package com.ftel.ptnetlibrary.ndpi.models

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Objects


class PortMapping {
    private val TAG = "PortMappings"
    private var mPrefs: SharedPreferences? = null
    private var mMapping = ArrayList<PortMap>()


    class PortMap(
        val ipproto: Int,
        val orig_port: Int,
        val redirect_port: Int,
        val redirect_ip: String
    ) {
        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val portMap = o as PortMap
            return ipproto == portMap.ipproto && orig_port == portMap.orig_port && redirect_port == portMap.redirect_port && redirect_ip == portMap.redirect_ip
        }

        override fun hashCode(): Int {
            return Objects.hash(ipproto, orig_port, redirect_port, redirect_ip)
        }
    }

    constructor()

    constructor(ctx: Context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        reload();
    }

    fun clear() {
        mMapping.clear()
    }

    fun save() {
        mPrefs!!.edit()
            .putString(Prefs.PREF_PORT_MAPPING, toJson(false))
            .apply()
    }

    private fun reload() {
        val serialized = mPrefs!!.getString(Prefs.PREF_PORT_MAPPING, "")
        if (serialized!!.isNotEmpty()) fromJson(serialized) else clear()
    }

    private fun fromJson(jsonStr: String?): Boolean {
        return try {
            val listOfMyClassObject: Type = object : TypeToken<ArrayList<PortMap?>?>() {}.type
            val gson = Gson()
            mMapping = gson.fromJson(jsonStr, listOfMyClassObject)
            true
        } catch (e: JsonParseException) {
            e.printStackTrace()
            false
        }
    }

    private fun toJson(prettyPrint: Boolean): String {
        val builder = GsonBuilder()
        if (prettyPrint) builder.setPrettyPrinting()
        val gson = builder.create()
        //Log.d(TAG, "toJson: " + serialized);
        return gson.toJson(mMapping)
    }

    // returns false if the mapping already exists
    fun add(mapping: PortMap?): Boolean {
        if (mMapping.contains(mapping)) return false
        mMapping.add(mapping!!)
        return true
    }

    fun remove(mapping: PortMap?): Boolean {
        return mMapping.remove(mapping)
    }

    fun iter(): Iterator<PortMap> {
        return mMapping.iterator()
    }
}