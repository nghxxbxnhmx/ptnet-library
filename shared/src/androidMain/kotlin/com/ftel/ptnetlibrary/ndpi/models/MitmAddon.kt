package com.ftel.ptnetlibrary.ndpi.models


import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import androidx.preference.PreferenceManager
import java.io.IOException
import java.lang.ref.WeakReference


import com.ftel.ptnetlibrary.ndpi.Utils

class MitmAddon(ctx: Context, receiver: MitmListener) {

    companion object {
        private const val PACKAGE_VERSION_CODE: Long = 18
        private const val PACKAGE_VERSION_NAME: String = "v1.1"
        private const val REPOSITORY: String = "https://github.com/emanuele-f/PCAPdroid-mitm"
        private const val TAG: String = "MitmAddon"

        @JvmStatic
        fun getInstalledVersion(ctx: Context): Long {
            return try {
                val pInfo = Utils.getPackageInfo(ctx.packageManager, MitmAPI.PACKAGE_NAME, 0)
                PackageInfoCompat.getLongVersionCode(pInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                -1
            }
        }

        @JvmStatic
        fun getUid(ctx: Context): Int {
            return try {
                Utils.getPackageUid(ctx.packageManager, MitmAPI.PACKAGE_NAME, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                -1
            }
        }

        @JvmStatic
        fun isInstalled(ctx: Context): Boolean {
            return getInstalledVersion(ctx) == PACKAGE_VERSION_CODE
        }

        @JvmStatic
        fun getGithubReleaseUrl(): String {
            return "$REPOSITORY/releases/download/$PACKAGE_VERSION_NAME/PCAPdroid-mitm_${PACKAGE_VERSION_NAME}_${Build.SUPPORTED_ABIS[0]}.apk"
        }

        @JvmStatic
        fun setCAInstallationSkipped(ctx: Context, skipped: Boolean) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            prefs.edit()
                .putBoolean(Prefs.PREF_CA_INSTALLATION_SKIPPED, skipped)
                .apply()
        }

        @JvmStatic
        fun isCAInstallationSkipped(ctx: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            return prefs.getBoolean(Prefs.PREF_CA_INSTALLATION_SKIPPED, false)
        }

        @JvmStatic
        fun setDecryptionSetupDone(ctx: Context, done: Boolean) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            prefs.edit()
                .putBoolean(Prefs.PREF_TLS_DECRYPTION_SETUP_DONE, done)
                .apply()
        }

        @JvmStatic
        fun needsSetup(ctx: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

            if (!Prefs.isTLSDecryptionSetupDone(prefs))
                return true

            if (!isInstalled(ctx)) {
                setDecryptionSetupDone(ctx, false)
                return true
            }

            return false
        }

        @SuppressLint("ObsoleteSdkInt")
        @JvmStatic
        fun isDozeEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !manager.isIgnoringBatteryOptimizations(
                MitmAPI.PACKAGE_NAME
            )
        }
    }

    private val mContext: Context = ctx.applicationContext
    private val mReceiver: MitmListener = receiver
    private val mMessenger: Messenger = Messenger(ReplyHandler(ctx.mainLooper, receiver))
    private var mService: Messenger? = null
    private var mStopRequested: Boolean = false

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.i(TAG, "Service connected")
            mService = Messenger(service)

            if (mStopRequested)
                stopProxy()
            else
                mReceiver.onMitmServiceConnect()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.i(TAG, "Service disconnected")
            disconnect()
            mReceiver.onMitmServiceDisconnect()
        }

        override fun onBindingDied(name: ComponentName) {
            Log.w(TAG, "onBindingDied")
            disconnect()
            mReceiver.onMitmServiceDisconnect()
        }

        override fun onNullBinding(name: ComponentName) {
            Log.w(TAG, "onNullBinding")
            disconnect()
            mReceiver.onMitmServiceDisconnect()
        }
    }

    private class ReplyHandler(looper: Looper, receiver: MitmListener) : Handler(looper) {
        private val mReceiver: WeakReference<MitmListener> = WeakReference(receiver)

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "Message: ${msg.what}")

            val receiver = mReceiver.get() ?: return

            if (msg.what == MitmAPI.MSG_GET_CA_CERTIFICATE) {
                val caPem: String? = msg.data?.getString(MitmAPI.CERTIFICATE_RESULT)
                receiver.onMitmGetCaCertificateResult(caPem)
            }
        }
    }

    fun connect(extraFlags: Int): Boolean {
        val intent = Intent().apply {
            component = ComponentName(MitmAPI.PACKAGE_NAME, MitmAPI.MITM_SERVICE)
        }

        return if (!mContext.bindService(
                intent,
                mConnection,
                Context.BIND_AUTO_CREATE or Context.BIND_ALLOW_ACTIVITY_STARTS or extraFlags
            )
        ) {
            try {
                mContext.unbindService(mConnection)
            } catch (ignored: IllegalArgumentException) {
                Log.w(TAG, "unbindService failed")
            }
            mService = null
            false
        } else {
            true
        }
    }

    fun disconnect() {
        mService?.let {
            Log.i(TAG, "Unbinding service...")
            try {
                mContext.unbindService(mConnection)
            } catch (ignored: IllegalArgumentException) {
                Log.w(TAG, "unbindService failed")
            }
            mService = null
        }
    }

    fun isConnected(): Boolean {
        return mService != null
    }

    fun requestCaCertificate(): Boolean {
        if (mService == null) {
            Log.e(TAG, "Not connected")
            return false
        }

        val msg = Message.obtain(null, MitmAPI.MSG_GET_CA_CERTIFICATE).apply {
            replyTo = mMessenger
        }

        return try {
            mService?.send(msg)
            true
        } catch (e: RemoteException) {
            e.printStackTrace()
            false
        }
    }

    fun startProxy(conf: MitmAPI.MitmConfig): ParcelFileDescriptor? {
        if (mService == null) {
            Log.e(TAG, "Not connected")
            return null
        }

        val pair = try {
            ParcelFileDescriptor.createReliableSocketPair()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        val msg = Message.obtain(null, MitmAPI.MSG_START_MITM, 0, 0, pair[0]).apply {
            data = Bundle().apply {
                putSerializable(MitmAPI.MITM_CONFIG, conf)
            }
        }

        return try {
            mService?.send(msg)
            Utils.safeClose(pair[0])
            pair[1]
        } catch (e: RemoteException) {
            e.printStackTrace()
            Utils.safeClose(pair[0])
            Utils.safeClose(pair[1])
            null
        }
    }

    fun stopProxy(): Boolean {
        if (mService == null) {
            Log.i(TAG, "Not connected, postponing stop message")
            mStopRequested = true
            return true
        }

        Log.i(TAG, "Send stop message")
        val msg = Message.obtain(null, MitmAPI.MSG_STOP_MITM)

        return try {
            mService?.send(msg)
            mStopRequested = false
            true
        } catch (e: RemoteException) {
            e.printStackTrace()
            false
        }
    }

    fun disableDoze(): Boolean {
        if (mService == null)
            return false

        Log.i(TAG, "Send disable doze")
        val msg = Message.obtain(null, MitmAPI.MSG_DISABLE_DOZE)

        return try {
            mService?.send(msg)
            true
        } catch (e: RemoteException) {
            e.printStackTrace()
            false
        }
    }
}
