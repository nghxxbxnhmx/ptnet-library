package com.ftel.ptnetlibrary.ndpi

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.Utils.getLocalizedConfig
import com.ftel.ptnetlibrary.ndpi.models.Blacklists
import com.ftel.ptnetlibrary.ndpi.models.Blocklist
import com.ftel.ptnetlibrary.ndpi.models.CtrlPermissions
import com.ftel.ptnetlibrary.ndpi.models.MatchList
import com.ftel.ptnetlibrary.ndpi.models.Prefs
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import com.ftel.ptnetlibrary.ndpi.Utils
import java.lang.ref.WeakReference


class PCAPdroid : Application() {
    private val TAG = "PCAPdroid"
    private var mVisMask: MatchList? = null
    private var mMalwareWhitelist: MatchList? = null
    private var mFirewallWhitelist: MatchList? = null
    private var mDecryptionList: MatchList? = null
    private var mBlocklist: Blocklist? = null
    private val mBlacklists: Blacklists? = null

    private var mCtrlPermissions: CtrlPermissions? = null
    private var mLocalizedContext: Context? = null
    private var mInstance: WeakReference<PCAPdroid>? = null
    protected var isUnderTest = false

    override fun onCreate() {
        super.onCreate()
        mInstance = WeakReference(this)
        mLocalizedContext = createConfigurationContext(getLocalizedConfig(this))
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        var theme = prefs.getString(Prefs.PREF_APP_THEME, "")

        // Listen to package events
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addDataScheme("package")
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_PACKAGE_ADDED == intent.action) {
                    val newInstall = !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    val packageName = intent.data!!.schemeSpecificPart
                    Log.d(TAG, "ACTION_PACKAGE_ADDED [new=$newInstall]: $packageName")
                    if (newInstall) checkUidMapping(packageName)
                } else if (Intent.ACTION_PACKAGE_REMOVED == intent.action) {
                    val isUpdate = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    val packageName = intent.data!!.schemeSpecificPart
                    Log.d(TAG, "ACTION_PACKAGE_REMOVED [update=$isUpdate]: $packageName")
                    if (!isUpdate) {
                        checkUidMapping(packageName)
                        removeUninstalledAppsFromAppFilter()
                    }
                }
            }
        }, filter)
        removeUninstalledAppsFromAppFilter()
    }

    fun getInstance(): PCAPdroid {
        return mInstance!!.get()!!
    }

    private fun checkUidMapping(pkg: String) {
        if (mVisMask != null) mVisMask!!.uidMappingChanged(pkg)

        // When an app is installed/uninstalled, recheck the UID mappings.
        // In particular:
        //  - On app uninstall, invalidate any package_name -> UID mapping
        //  - On app install, add the new package_name -> UID mapping
        if (mMalwareWhitelist != null && mMalwareWhitelist!!.uidMappingChanged(pkg)) {
            CaptureService.reloadMalwareWhitelist()
        }
        if (mFirewallWhitelist != null && mFirewallWhitelist!!.uidMappingChanged(pkg)) {
            if (CaptureService.isServiceActive()) {
                CaptureService.requireInstance()
                    .reloadFirewallWhitelist()
            }
        }
        if (mDecryptionList != null && mDecryptionList!!.uidMappingChanged(pkg)) {
            CaptureService.reloadDecryptionList()
        }
        if (mBlocklist != null && mBlocklist!!.uidMappingChanged(pkg)) {
            if (CaptureService.isServiceActive()) {
                CaptureService.requireInstance().reloadBlocklist()
            }
        }
    }

    private fun removeUninstalledAppsFromAppFilter() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val filter = Prefs.getAppFilter(prefs)?.toMutableSet()
        val toRemove = mutableListOf<String>()
        val pm = packageManager

        if (filter != null) {
            for (packageName in filter) {
                try {
                    Utils.getPackageInfo(pm, packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.i(TAG, "Package $packageName uninstalled, removing from app filter")
                    toRemove.add(packageName)
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            filter?.removeAll(toRemove.toSet())
            prefs.edit()
                .putStringSet(Prefs.PREF_APP_FILTER, filter)
                .apply()
        }
    }

    fun getVisualizationMask(): MatchList {
        if (mVisMask == null) mVisMask =
            MatchList(mLocalizedContext!!, Prefs.PREF_VISUALIZATION_MASK)
        return mVisMask!!
    }

    fun getDecryptionList(): MatchList {
        if (mDecryptionList == null) mDecryptionList =
            MatchList(mLocalizedContext!!, Prefs.PREF_DECRYPTION_LIST)
        return mDecryptionList!!
    }

    fun getMalwareWhitelist(): MatchList? {
        if (mMalwareWhitelist == null) mMalwareWhitelist =
            MatchList(mLocalizedContext!!, Prefs.PREF_MALWARE_WHITELIST)
        return mMalwareWhitelist
    }

    fun getBlocklist(): Blocklist? {
        if (mBlocklist == null) mBlocklist = Blocklist(mLocalizedContext!!)
        return mBlocklist
    }

    fun getFirewallWhitelist(): MatchList? {
        if (mFirewallWhitelist == null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            mFirewallWhitelist = MatchList(mLocalizedContext!!, Prefs.PREF_FIREWALL_WHITELIST)
            if (!Prefs.isFirewallWhitelistInitialized(prefs)) {
                initFirewallWhitelist()
                Prefs.setFirewallWhitelistInitialized(prefs)
            }
        }
        return mFirewallWhitelist
    }

    private fun initFirewallWhitelist() {
        val firewallWhitelist = mFirewallWhitelist!!

        firewallWhitelist.addApp(0 /* root */)
        firewallWhitelist.addApp(1000 /* android */)
        firewallWhitelist.addApp(packageName /* PCAPdroid */)

        // see also https://github.com/microg/GmsCore/issues/1508#issuecomment-876269198
        firewallWhitelist.addApp("com.google.android.gms" /* Google Play Services */)
        firewallWhitelist.addApp("com.google.android.gsf" /* Google Services Framework (push notifications) */)
        firewallWhitelist.addApp("com.google.android.ims" /* Carrier Services */)
        firewallWhitelist.addApp("com.sec.spp.push" /* Samsung Push Service */)

        mFirewallWhitelist = firewallWhitelist

        mFirewallWhitelist!!.save()
    }

    fun getCtrlPermissions(): CtrlPermissions? {
        if (mCtrlPermissions == null) mCtrlPermissions = CtrlPermissions(this)
        return mCtrlPermissions
    }
}