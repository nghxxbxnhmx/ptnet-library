package com.ftel.ptnetlibrary.ndpi.services

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.CurrentActivityHolder
import com.ftel.ptnetlibrary.ndpi.Utils
import com.ftel.ptnetlibrary.ndpi.Utils.getRunningVpn
import com.ftel.ptnetlibrary.ndpi.interfaces.CaptureStartListener
import com.ftel.ptnetlibrary.ndpi.models.CaptureSettings
import com.ftel.ptnetlibrary.utils.getAppContext

import com.ftel.ptnetlibrary.R

@SuppressLint("ObsoleteSdkInt", "AnnotateVersionCheck","MissingPermission")
class VpnReconnectService : Service() {
    private val TAG = "VpnReconnectService"
    private val NOTIFY_CHAN_VPNRECONNECT = "VPN Reconnection"
    val NOTIFY_ID_VPNRECONNECT = 10
    private val STOP_ACTION = "stop"

    private var INSTANCE: VpnReconnectService? = null
    private var mNetworkCallback: NetworkCallback? = null
    private lateinit var mHandler: Handler
    private lateinit var mActiveVpnNetwork: Network

    init {

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        mHandler = Handler(Looper.getMainLooper())
        INSTANCE = this
        super.onCreate()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        unregisterNetworkCallback()
        INSTANCE = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (intent.action != null && intent.action == STOP_ACTION) {
            Utils.showToastLong(this, R.string.vpn_reconnection_aborted)
            stopService()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(
            NOTIFY_ID_VPNRECONNECT,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        ) else startForeground(NOTIFY_ID_VPNRECONNECT, buildNotification())
        mHandler.postDelayed({
            Log.i(
                TAG,
                "Could not detect a VPN within the timeout, automatic reconnection aborted"
            )
            stopService()
        }, 10000)
        if (!registerNetworkCallbacks()) {
            stopService()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(
                NOTIFY_CHAN_VPNRECONNECT,
                NOTIFY_CHAN_VPNRECONNECT, NotificationManager.IMPORTANCE_LOW
            ) // low: no sound
            chan.setShowBadge(false)
            nm.createNotificationChannel(chan)
        }

        // Status notification builder: Not using now
        val currentActivity = CurrentActivityHolder.currentActivity
        var intent: Intent? = null
        currentActivity?.let {
            intent = Intent(getAppContext(), it::class.java)
        }

        var startMainApp: PendingIntent? = null
        if (intent != null) {
            startMainApp = PendingIntent.getActivity(
                this,
                0,
                intent,
                Utils.getIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }

        val abortReconnectIntent = Intent(
            this,
            VpnReconnectService::class.java
        )
        abortReconnectIntent.setAction(STOP_ACTION)
        val abortReconnect =
            PendingIntent.getService(this, 0, abortReconnectIntent, Utils.getIntentFlags(0))
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, NOTIFY_CHAN_VPNRECONNECT)
                .setSmallIcon(R.drawable.ic_logo)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentIntent(startMainApp!!)
                .setDeleteIntent(abortReconnect)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentTitle(getString(R.string.vpn_reconnection))
                .setContentText(getString(R.string.waiting_for_vpn_disconnect))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW) // see IMPORTANCE_LOW
        Log.d(TAG, "running")
        return builder.build()
    }

    private fun checkAvailableNetwork(cm: ConnectivityManager, network: Network) {
        if (network == mActiveVpnNetwork) return
        val cap = cm.getNetworkCapabilities(network)
        if (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            mActiveVpnNetwork = network
            Log.d(TAG, "Detected active VPN network: $mActiveVpnNetwork")

            // cancel the deadline timer / onLost timer
            mHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun registerNetworkCallbacks(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        mNetworkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "onAvailable: $network")
                checkAvailableNetwork(cm, network)
            }

            @RequiresApi(Build.VERSION_CODES.R)
            override fun onLost(network: Network) {
                Log.d(TAG, "onLost: $network")

                // NOTE: when onLost is called, the TRANSPORT_VPN capability may already have been removed
                if (network == mActiveVpnNetwork) {
                    // NOTE: onAvailable and onLost may be called multiple times before the actual VPN is started.
                    // Use a debounce delay to prevent mis-detection
                    mHandler.postDelayed({
                        Log.i(TAG, "Active VPN disconnected, starting the capture")
                        unregisterNetworkCallback()
                        val ctx: Context = this@VpnReconnectService
                        val settings = CaptureSettings(
                            ctx,
                            PreferenceManager.getDefaultSharedPreferences(ctx)
                        )
                        val helper = CaptureHelper(ctx)
                        helper.setListener(object : CaptureStartListener {
                            override fun onCaptureStartResult(success: Boolean) {
                                stopService()
                            }
                        })
                        helper.startCapture(settings)
                    }, 3000)
                }
            }
        }
        try {
            Log.d(TAG, "registerNetworkCallback")
            val builder = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

            // necessary to see other apps network events on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setIncludeOtherUidNetworks(
                true
            )
            cm.registerNetworkCallback(builder.build(), mNetworkCallback!!)
        } catch (e: SecurityException) {
            // this is a bug in Android 11 - https://issuetracker.google.com/issues/175055271?pli=1
            e.printStackTrace()
            Log.e(TAG, "registerNetworkCallback failed")
            mNetworkCallback = null
            return false
        }

        // The VPN may already be active
        val net = getRunningVpn(this)
        net?.let { checkAvailableNetwork(cm, it) }
        return true
    }

    private fun unregisterNetworkCallback() {
        if (mNetworkCallback != null) {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                Log.d(TAG, "unregisterNetworkCallback")
                cm.unregisterNetworkCallback(mNetworkCallback!!)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "unregisterNetworkCallback failed: $e")
            }
            mNetworkCallback = null
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.BASE)
    fun isAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    fun stopService() {
        Log.d(TAG, "stopService called")
        val service = INSTANCE ?: return
        service.unregisterNetworkCallback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(
                STOP_FOREGROUND_REMOVE
            )
        } else {
            service.stopForeground(true)
        }
        service.stopSelf()
    }
}