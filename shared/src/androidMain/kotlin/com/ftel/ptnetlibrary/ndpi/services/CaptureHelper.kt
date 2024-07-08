package com.ftel.ptnetlibrary.ndpi.services

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ftel.ptnetlibrary.ndpi.interfaces.CaptureStartListener
import com.ftel.ptnetlibrary.ndpi.Utils.showToastLong
import com.ftel.ptnetlibrary.ndpi.models.CaptureSettings
import java.net.InetAddress
import java.net.UnknownHostException

import com.ftel.ptnetlibrary.R

class CaptureHelper {
    private val TAG = "CaptureHelper"
    private lateinit var mContext: Context
    private var mResolveHosts = false

    private var mSettings: CaptureSettings? = null
    private var mListener: CaptureStartListener? = null
    private var mLauncher: ActivityResultLauncher<Intent>? = null

    constructor(activity: ComponentActivity, resolveHost: Boolean) {
        mContext = activity
        mResolveHosts = resolveHost
        mLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(), this::captureServiceResult
        )
    }

    constructor(context: Context) {
        mContext = context
        mResolveHosts = true
        mLauncher = null
    }

    private fun captureServiceResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) resolveHosts() else if (mListener != null) {
            showToastLong(mContext, R.string.vpn_setup_failed)
            mListener!!.onCaptureStartResult(false)
        }
    }

    private fun startCaptureOk() {
        val intent = Intent(mContext, CaptureService::class.java)
        intent.putExtra("settings", mSettings)

        ContextCompat.startForegroundService(mContext, intent)
        Log.d(TAG, "startForegroundService -> CaptureService.run()")
        if (mListener != null) {
            mListener!!.onCaptureStartResult(true)
        }
    }

    private fun resolveHost(host: String): String? {
        Log.d(TAG, "Resolving host: $host")
        try {
            return InetAddress.getByName(host).hostAddress
        } catch (ignored: UnknownHostException) {
        }
        return null
    }

    private fun doResolveHosts(settings: CaptureSettings?): String? {
        // NOTE: hosts must be resolved before starting the VPN and in a separate thread
        val resolved: String
        if (settings == null) return null
        if (settings.socks5_enabled) {
            resolved = resolveHost(settings.socks5_proxy_address)!!
            if (resolved.isEmpty()) return settings.socks5_proxy_address

            if (resolved != settings.socks5_proxy_address) {
                Log.i(TAG, "Resolved SOCKS5 proxy address: $resolved")
                settings.socks5_proxy_address = resolved
            }
        }
        return null
    }

    private fun resolveHosts() {
        Log.d(TAG, "resolveHosts")
        if (!mResolveHosts) {
            startCaptureOk()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        Thread {
            val failedHost = doResolveHosts(mSettings)
            handler.post {
                if (mSettings == null) {
                    mListener!!.onCaptureStartResult(false)
                    return@post
                }
                if (failedHost == null) startCaptureOk() else {
                    showToastLong(
                        mContext,
                        R.string.host_resolution_failed,
                        failedHost
                    )
                    mListener!!.onCaptureStartResult(false)
                }
            }
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun startCapture(settings: CaptureSettings) {
        if (CaptureService.isServiceActive()) {
            Log.d(TAG, "Close opened Service")
            CaptureService.stopService()
        }

        Log.d(TAG, "Handle Settings")
        mSettings = settings
        if (settings.root_capture || settings.readFromPcap()) {
            resolveHosts()
            return
        }

        var vpnPrepareIntent: Intent?
        try {
            vpnPrepareIntent = VpnService.prepare(mContext)
            Log.d(TAG, "VPN Prepare success")
        } catch (e: Exception) {
            e.printStackTrace()
            vpnPrepareIntent = null
            Log.d(TAG, "VPN Prepare failed")
        }

        if (vpnPrepareIntent != null) {
            if (mLauncher != null) AlertDialog.Builder(mContext)
                .setMessage(R.string.vpn_setup_msg)
                .setPositiveButton(R.string.ok) { dialog, whichButton ->
                    try {
                        mLauncher!!.launch(vpnPrepareIntent)
                    } catch (e: ActivityNotFoundException) {
                        showToastLong(mContext, R.string.no_intent_handler_found)
                        mListener!!.onCaptureStartResult(false)
                    }
                }
                .setOnCancelListener { dialog ->
                    showToastLong(mContext, R.string.vpn_setup_failed)
                    mListener!!.onCaptureStartResult(false)
                }
                .show() else if (mListener != null) mListener!!.onCaptureStartResult(false)
        } else resolveHosts()
    }

    fun setListener(listener: CaptureStartListener) {
        mListener = listener
    }
}