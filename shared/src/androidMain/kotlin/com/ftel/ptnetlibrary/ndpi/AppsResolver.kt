package com.ftel.ptnetlibrary.ndpi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.SparseArray
import androidx.core.content.ContextCompat
import com.ftel.ptnetlibrary.ndpi.Utils.getPackageInfo
import com.ftel.ptnetlibrary.ndpi.Utils.getPackageUid
import com.ftel.ptnetlibrary.ndpi.interfaces.DrawableLoader
import com.ftel.ptnetlibrary.ndpi.models.AppDescriptor
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import com.ftel.ptnetlibrary.R

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class AppsResolver {
    private var mApps: SparseArray<AppDescriptor>? = null
    private var mPm: PackageManager? = null
    private var mContext: Context? = null
    private var getPackageInfoAsUser: Method? = null
    private var mFallbackToGlobalResolution = false
    private var mVirtualAppIcon: Drawable? = null

    constructor(context: Context) {
        mApps = SparseArray<AppDescriptor>()
        mContext = context
        mPm = context.packageManager;

        initVirtualApps();
    }

    companion object {
        private const val TAG = "AppsResolver"

        // Get the AppDescriptor corresponding to the given package name
        // No caching occurs. Virtual apps cannot be used.
        // This is public to provide a fast resolution alternative to getAppByPackage
        fun resolveInstalledApp(
            pm: PackageManager?,
            packageName: String,
            pmFlags: Int,
            warnNotFound: Boolean
        ): AppDescriptor? {
            val pinfo: PackageInfo = try {
                getPackageInfo(pm!!, packageName, pmFlags)
            } catch (e: PackageManager.NameNotFoundException) {
                if (warnNotFound) Log.w(
                    TAG,
                    "could not retrieve package: $packageName"
                )
                return null
            }
            return AppDescriptor(pm, pinfo)
        }

        fun resolveInstalledApp(
            pm: PackageManager?,
            packageName: String,
            pmFlags: Int
        ): AppDescriptor? {
            return resolveInstalledApp(pm, packageName, pmFlags, true)
        }
    }

    private fun initVirtualApps() {
        // Use loaders to only load the bitmap in memory if requested via AppDescriptor.getIcon()
        val virtualIconLoader = DrawableLoader {

            // cache this to avoid copies
            if (mVirtualAppIcon == null) mVirtualAppIcon =
                mContext?.let { ContextCompat.getDrawable(it, android.R.drawable.sym_def_app_icon) }
            mVirtualAppIcon
        }
        val unknownIconLoader =
            DrawableLoader { mContext?.let { ContextCompat.getDrawable(it, android.R.drawable.ic_menu_help) } }

        // https://android.googlesource.com/platform/system/core/+/master/libcutils/include/private/android_filesystem_config.h
        // NOTE: these virtual apps cannot be used as a permanent filter (via addAllowedApplication)
        // as they miss a valid package name
        mApps?.put(
            Utils.UID_UNKNOWN, mContext?.let {
                AppDescriptor(
                    it.getString(R.string.unknown_app),
                    unknownIconLoader, "unknown", Utils.UID_UNKNOWN, true
                )
                    .setDescription(it.getString(R.string.unknown_app_info))
            }
        )
        mApps?.put(
            0, mContext?.let {
                AppDescriptor(
                    "Root",
                    virtualIconLoader, "root", 0, true
                )
                    .setDescription(it.getString(R.string.root_app_info))
            }
        )
        mApps?.put(
            1000, mContext?.let {
                AppDescriptor(
                    "Android",
                    virtualIconLoader, "android", 1000, true
                )
                    .setDescription(it.getString(R.string.android_app_info))
            }
        )
        mApps?.put(
            1001, mContext?.let {
                AppDescriptor(
                    it.getString(R.string.phone_app),
                    virtualIconLoader, "phone", 1001, true
                )
                    .setDescription(it.getString(R.string.phone_app_info))
            }
        )
        mApps?.put(
            1013, AppDescriptor(
                "MediaServer",
                virtualIconLoader, "mediaserver", 1013, true
            )
        )
        mApps?.put(
            1020, AppDescriptor(
                "MulticastDNSResponder",
                virtualIconLoader, "multicastdnsresponder", 1020, true
            )
        )
        mApps?.put(
            1021, AppDescriptor(
                "GPS",
                virtualIconLoader, "gps", 1021, true
            )
        )
        mApps?.put(
            1051, mContext?.let {
                AppDescriptor(
                    "netd",
                    virtualIconLoader, "netd", 1051, true
                )
                    .setDescription(it.getString(R.string.netd_app_info))
            }
        )
        mApps?.put(
            9999, AppDescriptor(
                "Nobody",
                virtualIconLoader, "nobody", 9999, true
            )
        )
    }

    fun getAppByUid(uid: Int, pm_flags: Int): AppDescriptor? {
        var app: AppDescriptor? = mApps?.get(uid)
        if (app != null) return app

        // Map the uid to the package name(s)
        var packages: Array<String>? = null
        try {
            packages = mPm?.getPackagesForUid(uid)
        } catch (e: SecurityException) {
            // A SecurityException is normally raised when trying to query a package of another user/profile
            // without holding the INTERACT_ACROSS_USERS/INTERACT_ACROSS_PROFILES permissions
            e.printStackTrace()
        }
        if (packages.isNullOrEmpty()) {
            Log.w(TAG, "could not retrieve package: uid=$uid")
            return null
        }

        // Impose order to guarantee that a uid is always mapped to the same package name.
        // The mapping may change if a package sharing this UID is installed/removed.
        // For simplicity we ignore this change at runtime, and only address it in persistent data
        // (e.g. in the MatchList to ensure that a user can always remove rules see #257)
        var packageName = packages[0]
        for (pkg in packages) {
            if (pkg.compareTo(packageName) < 0) packageName = pkg
        }

        // In case of root capture, we may be capturing traffic of different users/work profiles.
        // To get the correct label and icon, try to resolve the app as the specific user of the connection.
        if (!mFallbackToGlobalResolution && CaptureService().isCapturingAsRoot()) {
            try {
                if (getPackageInfoAsUser == null) getPackageInfoAsUser =
                    PackageManager::class.java.getDeclaredMethod(
                        "getPackageInfoAsUser",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                val pinfo = getPackageInfoAsUser!!.invoke(
                    mPm,
                    packageName,
                    pm_flags,
                    Utils.getUserId(uid)
                ) as PackageInfo?

                if (pinfo != null) app = mPm?.let { AppDescriptor(it, pinfo) }

            } catch (e: NoSuchMethodException) {
                Log.w(TAG, "getPackageInfoAsUser call fails, falling back to standard resolution")
                e.printStackTrace()
                mFallbackToGlobalResolution = true
            } catch (e: IllegalAccessException) {
                Log.w(TAG, "getPackageInfoAsUser call fails, falling back to standard resolution")
                e.printStackTrace()
                mFallbackToGlobalResolution = true
            } catch (e: InvocationTargetException) {
                Log.w(TAG, "getPackageInfoAsUser call fails, falling back to standard resolution")
                e.printStackTrace()
                mFallbackToGlobalResolution = true
            }
        }
        if (app == null) app = resolveInstalledApp(mPm, packageName, pm_flags)

        if (app != null) mApps?.put(uid, app)
        return app
    }

    fun getAppByPackage(packageName: String, pmFlags: Int): AppDescriptor? {
        val uid = getUid(packageName)
        return if (uid == Utils.UID_NO_FILTER) null else getAppByUid(uid, pmFlags)
    }

    /* Lookup a UID by package name (including virtual apps).
     * UID_NO_FILTER is returned if no match is found. */
    fun getUid(packageName: String): Int {
        if (!packageName.contains(".")) {
            // This is a virtual app
            for (i in 0 until mApps?.size()!!) {
                val app: AppDescriptor? = mApps?.valueAt(i)
                if (app!!.getPackageName() == packageName) return app.getUid()
            }
        } else {
            try {
                return getPackageUid(mPm!!, packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(
                    TAG,
                    "Could not retrieve package $packageName"
                )
                //e.printStackTrace();
            }
        }

        // Not found
        return Utils.UID_NO_FILTER
    }

    fun clear() {
        mApps?.clear()
        initVirtualApps()
    }
}