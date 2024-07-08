package com.ftel.ptnetlibrary.ndpi.models

import android.content.Context
import android.content.SharedPreferences
import android.util.ArrayMap
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.models.MatchList.Rule
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type


class CtrlPermissions {

    private val mRules: ArrayMap<String, Rule> = ArrayMap()
    private var mPrefs: SharedPreferences? = null


    enum class ConsentType {
        UNSPECIFIED,
        ALLOW,
        DENY
    }

    companion object {
        private val PREF_NAME = "ctrl_perms"

        class Rule {
            var package_name: String? = null
            var consent: ConsentType? = null

            constructor(_package_name: String?, tp: ConsentType?) {
                package_name = _package_name
                consent = tp
            }
        }


        class Serializer : JsonSerializer<CtrlPermissions> {
            override fun serialize(
                src: CtrlPermissions,
                typeOfSrc: Type?,
                context: JsonSerializationContext?
            ): JsonElement {
                val result = JsonObject()
                val rulesObj = JsonObject()
                for (rule in src.mRules.values) {
                    rulesObj.add(rule.package_name, JsonPrimitive(rule.consent.toString()))
                }
                result.add("rules", rulesObj)
                return result
            }
        }

    }


    constructor(ctx: Context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        reload();
    }

    fun reload() {
        val serialized = mPrefs!!.getString(PREF_NAME, "")
        //Log.d(TAG, serialized);
        if (serialized!!.isNotEmpty()) {
            val obj = JsonParser.parseString(serialized).getAsJsonObject()
            deserialize(obj)
        } else mRules.clear()
    }

    private fun deserialize(obj: JsonObject) {
        mRules.clear()
        val rules = obj.getAsJsonObject("rules") ?: return
        for ((key, value) in rules.entrySet()) {
            if (value.isJsonPrimitive && value.getAsJsonPrimitive().isString) {
                val `val` = value.getAsJsonPrimitive().getAsString()
                try {
                    val tp = ConsentType.valueOf(`val`)
                    mRules[key] = Rule(key, tp)
                } catch (ignored: IllegalArgumentException) {
                }
            }
        }
    }

    private fun save() {
        val gson = GsonBuilder().registerTypeAdapter(javaClass, Serializer())
            .create()
        val serialized = gson.toJson(this)
        //Log.d(TAG, "json: " + serialized);
        mPrefs!!.edit()
            .putString(PREF_NAME, serialized)
            .apply()
    }

    fun add(packageName: String?, tp: ConsentType?) {
        mRules[packageName] = Rule(packageName, tp)
        save()
    }

    fun remove(packageName: String?) {
        mRules.remove(packageName)
        save()
    }

    fun removeAll() {
        mRules.clear()
        save()
    }

    fun iterRules(): Iterator<Rule> {
        return mRules.values.iterator()
    }

    fun hasRules(): Boolean {
        return !mRules.isEmpty()
    }

    fun getConsent(packageName: String?): ConsentType? {
        val rule = mRules[packageName] ?: return ConsentType.UNSPECIFIED
        return rule.consent
    }
}