package com.ftel.ptnetlibrary.ndpi.models

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.util.Log
import com.ftel.ptnetlibrary.ndpi.interfaces.DrawableLoader
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.Serializable
import java.util.Locale


class AppDescriptor : Comparable<AppDescriptor>, Serializable {
    private var mName: String? = null
    private var mPackageName: String? = null
    private var mUid = 0
    private var mIsSystem = false
    var mIcon: Drawable? = null
    private var mIconLoader: DrawableLoader? = null
    private var mDescription: String? = null

    companion object {
        private const val TAG = "AppDescriptor"
        private var badgedIconFails = false
    }


    // NULL for virtual apps
    var mPm: PackageManager? = null
    var mPackageInfo: PackageInfo? = null


    constructor(
        name: String?,
        icon_loader: DrawableLoader?,
        package_name: String?,
        uid: Int,
        is_system: Boolean
    ) {
        mName = name
        mIcon = null
        mIconLoader = icon_loader
        mPackageName = package_name
        mUid = uid
        mIsSystem = is_system
        mDescription = ""
    }

    constructor(pm: PackageManager, pkgInfo: PackageInfo) : this(
        pkgInfo.applicationInfo.loadLabel(pm).toString(), null,
        pkgInfo.applicationInfo.packageName, pkgInfo.applicationInfo.uid,
        pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    ) {
        mPm = pm
        mPackageInfo = pkgInfo
    }


    fun setDescription(dsc: String): AppDescriptor {
        mDescription = dsc
        return this
    }

    fun getDescription(): String {
        return mDescription!!
    }

    fun getName(): String {
        return mName!!
    }

    @SuppressLint("ObsoleteSdkInt")
    fun getIcon(): Drawable? {
        if (mIcon != null) {
            return mIcon
        }

        if (mIconLoader != null) {
            mIcon = mIconLoader!!.getDrawable()
            return mIcon
        }

        if (mPackageInfo == null || mPm == null) {
            return null
        }

        // NOTE: this call is expensive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && CaptureService().isCapturingAsRoot()) {
            // Contrary to "loadIcon", this returns the correct icon for main-profile apps
            // when PCAPdroid is running into a work profile with root. For work-profile apps,
            // the badge is added below via getUserHandleForUid
            mIcon = mPackageInfo!!.applicationInfo.loadUnbadgedIcon(mPm)

            if (!badgedIconFails) {
                try {
                    val handle = UserHandle.getUserHandleForUid(mUid)

                    // On some systems may throw "java.lang.SecurityException: You need MANAGE_USERS permission to:
                    // check if specified user a managed profile outside your profile group"
                    mIcon = mPm!!.getUserBadgedIcon(mIcon!!, handle)
                } catch (e: SecurityException) {
                    Log.w(TAG, "getUserBadgedIcon failed, using icons without badges: " + e.message)
                    badgedIconFails = true
                }
            }
        } else {
            mIcon = mPackageInfo!!.applicationInfo.loadIcon(mPm)
        }

        return mIcon
    }

    fun getPackageName(): String {
        return mPackageName!!
    }

    fun getUid(): Int {
        return mUid
    }

    fun isSystem(): Boolean {
        return mIsSystem
    }

    // the app does not have a package name (e.g. uid 0 is android system)
    fun isVirtual(): Boolean {
        return mPackageInfo == null
    }

    fun getPackageInfo(): PackageInfo? {
        return mPackageInfo
    }

    override fun compareTo(other: AppDescriptor): Int {
        var rv = getName().lowercase(Locale.getDefault())
            .compareTo(other.getName().lowercase(Locale.getDefault()))

        if (rv == 0) {
            rv = getPackageName().compareTo(other.getPackageName())
        }

        return rv
    }

    fun matches(filter: String, exactPackage: Boolean): Boolean {
        val packageName = getPackageName().lowercase(Locale.getDefault())

        return getName().lowercase(Locale.getDefault()).contains(filter) ||
                (exactPackage && packageName == filter) ||
                (!exactPackage && packageName.contains(filter))
    }

    override fun toString(): String {
        return "AppDescriptor(mName='$mName', mIcon=$mIcon, mIconLoader=$mIconLoader, mPackageName='$mPackageName', mUid=$mUid, mIsSystem=$mIsSystem, mDescription='$mDescription', mPm=$mPm, mPackageInfo=$mPackageInfo)"
    }
}