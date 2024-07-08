package com.ftel.ptnetlibrary.ndpi.services

// Personal Class

// Library class

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.SparseArray
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.Utils.PrivateDnsMode
import com.ftel.ptnetlibrary.ndpi.Utils.showToastLong
import com.ftel.ptnetlibrary.ndpi.Utils.trimlvl2str
import com.ftel.ptnetlibrary.ndpi.interfaces.PcapDumper
import com.ftel.ptnetlibrary.ndpi.models.Billing
import com.ftel.ptnetlibrary.ndpi.models.BlacklistDescriptor
import com.ftel.ptnetlibrary.ndpi.models.Blacklists
import com.ftel.ptnetlibrary.ndpi.models.Blacklists.NativeBlacklistStatus
import com.ftel.ptnetlibrary.ndpi.models.Blocklist
import com.ftel.ptnetlibrary.ndpi.models.CaptureSettings
import com.ftel.ptnetlibrary.ndpi.models.CaptureStats
import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor
import com.ftel.ptnetlibrary.ndpi.models.ConnectionUpdate
import com.ftel.ptnetlibrary.ndpi.models.FilterDescriptor
import com.ftel.ptnetlibrary.ndpi.models.MatchList
import com.ftel.ptnetlibrary.ndpi.models.MatchList.ListDescriptor
import com.ftel.ptnetlibrary.ndpi.models.MitmAPI
import com.ftel.ptnetlibrary.ndpi.models.MitmAddon.Companion.getUid
import com.ftel.ptnetlibrary.ndpi.models.PortMapping
import com.ftel.ptnetlibrary.ndpi.models.Prefs
import com.ftel.ptnetlibrary.ndpi.models.Prefs.DumpMode
import com.ftel.ptnetlibrary.ndpi.models.Prefs.PayloadMode
import com.ftel.ptnetlibrary.ndpi.pcap_dump.FileDumper
import com.ftel.ptnetlibrary.ndpi.pcap_dump.HTTPServer
import com.ftel.ptnetlibrary.ndpi.pcap_dump.UDPDumper
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Enumeration
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

import com.ftel.ptnetlibrary.R
import com.ftel.ptnetlibrary.ndpi.*
import com.ftel.ptnetlibrary.utils.getAppContext

@SuppressLint("MissingPermission", "ObsoleteSdkInt", "NewApi")
class CaptureService : VpnService(), Runnable {
    lateinit var APP_VERSION: String
    lateinit var DEVICE_MODEL: String
    lateinit var OS_VERSION: String

    val mLock = ReentrantLock()
    val mCaptureStopped: Condition = mLock.newCondition()
    private var mParcelFileDescriptor: ParcelFileDescriptor? = null
    private var mIsAlwaysOnVPN = false
    private var mRevoked = false
    private var mPrefs: SharedPreferences? = null
    private var mHandler: Handler? = null
    private var mCaptureThread: Thread? = null
    private var mBlacklistsUpdateThread: Thread? = null
    private var mConnUpdateThread: Thread? = null
    private var mDumperThread: Thread? = null
    private var mDumpQueue: LinkedBlockingDeque<ByteArray>? = null
    private var vpn_ipv4: String? = null
    private var vpn_dns: String? = null
    private var dns_server: String? = null
    private var last_bytes: Long = 0
    private var last_connections = 0L
    private var mAppFilterUids: IntArray? = null
    private var mPcapUri: Uri? = null
    private var mPcapFname: String? = null
    private var mStatusBuilder: NotificationCompat.Builder? = null
    private var mMalwareBuilder: NotificationCompat.Builder? = null
    private var mMonitoredNetwork: Long = 0
    private var mNetworkCallback: NetworkCallback? = null

    // can only be accessed by native code to avoid concurrency issues
    private var nativeAppsResolver: AppsResolver? = null

    private var mMalwareDetectionEnabled = false
    private var mBlacklistsUpdateRequested = false
    private var mFirewallEnabled = false
    private var mBlockPrivateDns = false
    private var mDnsEncrypted = false
    private var mStrictDnsNoticeShown = false
    private var mQueueFull = false
    private var mStopping = false
    private var mIfIndexToName: SparseArray<String>? = null
    private var mSocks5Enabled = false
    private var mSocks5Address: String? = null
    private var mSocks5Port = 0
    private var mSocks5Auth: String? = null
    private var mLowMemory = false
    private var mNewAppsInstallReceiver: BroadcastReceiver? = null


    private val mPendingUpdates: LinkedBlockingDeque<Pair<Array<ConnectionDescriptor?>?, Array<ConnectionUpdate?>?>> =
        LinkedBlockingDeque<Pair<Array<ConnectionDescriptor?>?, Array<ConnectionUpdate?>?>>(32)
    private var mDumper: PcapDumper? = null
    private var mMitmReceiver: MitmReceiver? = null
    private var conn_reg: ConnectionsRegister? = null
    private var mBlacklists: Blacklists? = null
    private var mBlocklist: Blocklist? = null
    private val lastStats: MutableLiveData<CaptureStats> = MutableLiveData<CaptureStats>()
    private val serviceStatus = MutableLiveData<ServiceStatus>()
    private var mMalwareWhitelist: MatchList? = null
    private var mFirewallWhitelist: MatchList? = null
    private var mDecryptionList: MatchList? = null
    private var mPrivateDnsMode: PrivateDnsMode? = null
    private var mBilling: Billing? = null
    private var mSettings: CaptureSettings? = null

    //    private var INSTANCE: CaptureService? = null
    companion object {
        private val TAG = "CaptureService"
        private var INSTANCE: CaptureService? = null
        val VpnSessionName = "PCAPdroid VPN"
        val NOTIFY_CHAN_VPNSERVICE = "VPNService"
        val NOTIFY_CHAN_MALWARE_DETECTION = "Malware detection"
        val NOTIFY_CHAN_OTHER = "Other"
        val VPN_MTU = 10000
        val NOTIFY_ID_VPNSERVICE = 1
        val NOTIFY_ID_LOW_MEMORY = 2
        val NOTIFY_ID_APP_BLOCKED = 3
        private var HAS_ERROR = false

        /* The maximum connections to log into the ConnectionsRegister. Older connections are dropped.
 * Max estimated memory usage: less than 4 MB (+8 MB with payload mode minimal). */
        val CONNECTIONS_LOG_SIZE = 8192

        /* The IP address of the virtual network interface */
        val VPN_IP_ADDRESS = "10.215.173.1"
        val VPN_IP6_ADDRESS = "fd00:2:fd00:1:fd00:1:fd00:1"

        /* The DNS server IP address to use to internally analyze the DNS requests.
         * It must be in the same subnet of the VPN network interface.
         * After the analysis, requests will be routed to the primary DNS server. */
        val VPN_VIRTUAL_DNS_SERVER = "10.215.173.2"


        fun requireInstance(): CaptureService {
            return INSTANCE!!
        }

        fun requireConnsRegister(): ConnectionsRegister {
            return getConnsRegister()!!
        }

        @SuppressLint("ObsoleteSdkInt")
        fun stopService() {
            val captureService = INSTANCE
            Log.d(TAG, "stopService called (instance? " + (captureService != null) + ")")
            if (captureService == null) return
            captureService.mStopping = true
            stopPacketLoop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) captureService.stopForeground(
                STOP_FOREGROUND_REMOVE
            ) else captureService.stopForeground(true)
            captureService.stopSelf()
        }


        fun reloadMalwareWhitelist() {
            if (INSTANCE == null || !INSTANCE!!.mMalwareDetectionEnabled) return
            Log.i(TAG, "reloading malware whitelist")
            reloadMalwareWhitelist(INSTANCE!!.mMalwareWhitelist!!.toListDescriptor())
        }

        fun abortStart(): Int {
            stopService()
            requireInstance().updateServiceStatus(ServiceStatus.STOPPED)
            return START_NOT_STICKY
        }

        fun getDumpMode(): DumpMode {
            return if (INSTANCE != null) INSTANCE!!.mSettings!!.dump_mode else DumpMode.NONE
        }

        fun getCurPayloadMode(): PayloadMode {
            if (INSTANCE == null) return PayloadMode.MINIMAL
            return if (INSTANCE!!.mSettings!!.full_payload) PayloadMode.FULL else PayloadMode.MINIMAL
        }

        fun getInstance(): CaptureService {
            if (INSTANCE == null) {
                INSTANCE = CaptureService()
            }
            return INSTANCE!!
        }

        fun getConnsRegister(): ConnectionsRegister? {
            return INSTANCE?.conn_reg
        }


        fun checkAvailableHeap() {
            // This does not account per-app jvm limits
            val availableHeap: Long = Utils.getAvailableHeap()
            if (availableHeap <= Utils.LOW_HEAP_THRESHOLD) {
                Log.w(TAG, "Detected low HEAP memory: " + Utils.formatBytes(availableHeap))
                requireInstance().handleLowMemory()
            }
        }


        fun reloadDecryptionList() {
            if (INSTANCE == null || INSTANCE!!.mDecryptionList == null) return
            Log.i(TAG, "reloading TLS decryption whitelist")
            reloadDecryptionList(INSTANCE!!.mDecryptionList!!.toListDescriptor())
        }


        fun isVpnCapture(): Int {
            return if ((requireInstance().isRootCapture() or requireInstance().isPcapFileCapture()) == 1) 0 else 1
        }

        fun isLowMemory(): Boolean {
            return INSTANCE != null && INSTANCE!!.mLowMemory
        }

        fun isServiceActive(): Boolean {
            return INSTANCE?.mCaptureThread != null
        }


        /*
        Native C - jni_impl.c
        */

        external fun initLogger(path: String?, level: Int): Int

        external fun writeLog(logger: Int, lvl: Int, message: String?): Int

        private external fun initPlatformInfo(appver: String, device: String, os: String)

        private external fun runPacketLoop(fd: Int, vpn: CaptureService, sdk: Int)

        private external fun stopPacketLoop()

        private external fun getFdSetSize(): Int

        private external fun setPrivateDnsBlocked(to_block: Boolean)

        private external fun setDnsServer(server: String)

        private external fun addPortMapping(
            ipproto: Int,
            orig_port: Int,
            redirect_port: Int,
            redirect_ip: String
        )

        external fun reloadBlacklists()

        external fun reloadBlocklist(blocklist: ListDescriptor?): Boolean

        external fun reloadFirewallWhitelist(whitelist: ListDescriptor): Boolean

        external fun reloadMalwareWhitelist(whitelist: ListDescriptor): Boolean

        external fun reloadDecryptionList(whitelist: ListDescriptor): Boolean

        external fun askStatsDump()

        external fun getPcapHeader(): ByteArray?

        external fun nativeSetFirewallEnabled(enabled: Boolean)

        external fun getNumCheckedMalwareConnections(): Int

        external fun getNumCheckedFirewallConnections(): Int

        external fun rootCmd(prog: String?, args: String?): Int

        external fun setPayloadMode(mode: Int)

        external fun getL7Protocols(): List<String?>?

        external fun dumpMasterSecret(secret: ByteArray?)

        external fun hasSeenPcapdroidTrailer(): Boolean
    }

    enum class ServiceStatus {
        STOPPED,
        STARTED
    }

    init {
        try {
            System.loadLibrary("capture")
            APP_VERSION = Utils.getAppVersionString()
            DEVICE_MODEL = Utils.getDeviceModel()
            OS_VERSION = Utils.getOsVersion()
        } catch (e: UnsatisfiedLinkError) {
            // This should only happen while running tests
            e.printStackTrace()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.createConfigurationContext(Utils.getLocalizedConfig(base)))
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        nativeAppsResolver = AppsResolver(this)
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        // initialize to prevent NULL pointer exceptions in methods (e.g. isRootCapture)
        mSettings = CaptureSettings(this, mPrefs!!)
        INSTANCE = this
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mStopping = false

        // startForeground must always be called since the Service is being started with
        // ContextCompat.startForegroundService.
        // NOTE: since Android 12, startForeground cannot be called when the app is in background
        // (unless invoked via an Intent).
        setupNotifications()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) startForeground(
            NOTIFY_ID_VPNSERVICE,
            getStatusNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        ) else startForeground(NOTIFY_ID_VPNSERVICE, getStatusNotification())

        // NOTE: onStartCommand may be called when the capture is already running, e.g. if the user
        // turns on the always-on VPN while the capture is running in root mode
        if (mCaptureThread != null) {
            // Restarting the capture requires calling stopAndJoinThreads, which is blocking.
            // Choosing not to support this right now.
            Log.e(TAG, "Restarting the capture is not supported")
            return abortStart()
        }
        if (VpnReconnectService().isAvailable()) {
            VpnReconnectService().stopService()
        }

        mHandler = Handler(Looper.getMainLooper())
        mBilling = Billing.newInstance(this)
        Log.d(TAG, "onStartCommand")

        // NOTE: a null intent may be delivered due to START_STICKY
        // It can be simulated by starting the capture, putting PCAPdroid in the background and then running:
        //  adb shell ps | grep remote_capture | awk '{print $2}' | xargs adb shell run-as com.emanuelef.remote_capture.debug kill
        val settings: CaptureSettings? =
            if (intent == null) null else Utils.getSerializableExtra(
                intent, "settings",
                CaptureSettings::class.java
            )
        if (settings == null) {
            // Use the settings from mPrefs

            // An Intent without extras is delivered in case of always on VPN
            // https://developer.android.com/guide/topics/connectivity/vpn#always-on
            mIsAlwaysOnVPN = (intent != null)
            Log.i(TAG, "Missing capture settings, using SharedPrefs")
        } else {
            // Use the provided settings
            mSettings = settings
            mIsAlwaysOnVPN = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mIsAlwaysOnVPN =
            mIsAlwaysOnVPN or isAlwaysOn
        Log.d(TAG, "alwaysOn? $mIsAlwaysOnVPN")
        if (mIsAlwaysOnVPN) {
            mSettings!!.root_capture = false
            mSettings!!.input_pcap_path = null
        }
        if (mSettings!!.readFromPcap()) {
            // Disable incompatible settings
            mSettings!!.dump_mode = DumpMode.NONE
            mSettings!!.app_filter = HashSet<String>()
            mSettings!!.socks5_enabled = false
            mSettings!!.tls_decryption = false
            mSettings!!.root_capture = false
            mSettings!!.auto_block_private_dns = false
            mSettings!!.capture_interface = (mSettings!!.input_pcap_path)!!
        }

        // Retrieve DNS server
        val fallbackDnsV4 = Prefs.getDnsServerV4((mPrefs)!!)
        dns_server = fallbackDnsV4
        mBlockPrivateDns = false
        mStrictDnsNoticeShown = false
        mDnsEncrypted = false
        setPrivateDnsBlocked(false)

        // Map network interfaces
        mIfIndexToName = SparseArray()
        val ifaces: Enumeration<NetworkInterface> = Utils.getNetworkInterfaces()
        while (ifaces.hasMoreElements()) {
            val iface = ifaces.nextElement()
            Log.d(TAG, "ifidx " + iface.index + " -> " + iface.name)
            mIfIndexToName!!.put(iface.index, iface.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val cm = getSystemService(Service.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net: Network? = cm.activeNetwork
            if (net != null) {
                handleLinkProperties(cm.getLinkProperties(net))
                if (Prefs.useSystemDns((mPrefs)!!) || mSettings!!.root_capture) {
                    dns_server = Utils.getDnsServer(cm, net)
                    if (dns_server == null) dns_server = fallbackDnsV4 else {
                        mMonitoredNetwork = net.getNetworkHandle()
                        registerNetworkCallbacks()
                    }
                } else dns_server = fallbackDnsV4
            }
        }
        vpn_dns = VPN_VIRTUAL_DNS_SERVER
        vpn_ipv4 = VPN_IP_ADDRESS
        last_bytes = 0
        last_connections = 0
        mLowMemory = false
        conn_reg = ConnectionsRegister(this, CONNECTIONS_LOG_SIZE)
        mDumper = null
        mDumpQueue = null
        mPendingUpdates.clear()
        mPcapFname = null
        HAS_ERROR = false

        // Possibly allocate the dumper
        when (mSettings!!.dump_mode) {
            DumpMode.HTTP_SERVER -> {
                mDumper = HTTPServer(this, mSettings!!.http_server_port, mSettings!!.pcapng_format)
            }

            DumpMode.PCAP_FILE -> {
                mPcapFname =
                    if (mSettings!!.pcap_name.isNotEmpty()) {
                        mSettings!!.pcap_name
                    } else {
                        Utils.getUniquePcapFileName(
                            this,
                            mSettings!!.pcapng_format
                        )
                    }

                mPcapUri =
                    if (mSettings!!.pcap_uri.isNotEmpty()) {
                        Uri.parse(mSettings!!.pcap_uri)
                    } else {
                        Utils.getDownloadsUri(
                            this,
                            mPcapFname!!
                        )
                    }

                if (mPcapUri == null) return abortStart()

                mDumper = FileDumper(this, mPcapUri!!)
            }

            DumpMode.UDP_EXPORTER -> {
                val addr: InetAddress

                try {
                    addr = InetAddress.getByName(mSettings!!.collector_address)
                } catch (e: UnknownHostException) {
                    reportError(e.localizedMessage)
                    e.printStackTrace()
                    return abortStart()
                }

                mDumper = UDPDumper(
                    InetSocketAddress(addr, mSettings!!.collector_port),
                    mSettings!!.pcapng_format
                )
            }

            DumpMode.NONE -> Log.d(TAG, "DumpMode: ${mSettings!!.dump_mode}")
        }

        mDumper?.let {
            // Max memory usage = (JAVA_PCAP_BUFFER_SIZE * 64) = 32 MB
            mDumpQueue = LinkedBlockingDeque(64)

            try {
                mDumper!!.startDumper()
            } catch (e: IOException) {
                reportError(e.localizedMessage)
                e.printStackTrace()
                mDumper = null
                return abortStart()
            } catch (e: SecurityException) {
                reportError(e.localizedMessage)
                e.printStackTrace()
                mDumper = null
                return abortStart()
            }
        }

        mSocks5Address = ""
        mSocks5Enabled = mSettings!!.socks5_enabled || mSettings!!.tls_decryption

        if (mSocks5Enabled) {
            if (mSettings!!.tls_decryption) {
                // Built-in decryption
                mSocks5Address = "127.0.0.1"
                mSocks5Port = MitmReceiver().TLS_DECRYPTION_PROXY_PORT
                mSocks5Auth = "${Utils.genRandomString(8)}:${Utils.genRandomString(8)}"

                mMitmReceiver = MitmReceiver(this, mSettings!!, mSocks5Auth)
                try {
                    if (!mMitmReceiver!!.start()) return abortStart()
                } catch (e: IOException) {
                    e.printStackTrace()
                    return abortStart()
                }
            } else {
                // SOCKS5 proxy
                mSocks5Address = mSettings!!.socks5_proxy_address
                mSocks5Port = mSettings!!.socks5_proxy_port

                mSocks5Auth =
                    if (mSettings!!.socks5_username.isNotEmpty() && mSettings!!.socks5_password.isNotEmpty()) {
                        "${mSettings!!.socks5_username}:${mSettings!!.socks5_password}"
                    } else {
                        null
                    }
            }
        }

        mDecryptionList =
            if (mSettings!!.tls_decryption && !mSettings!!.root_capture && !mSettings!!.readFromPcap()) {
                PCAPdroid().getInstance().getDecryptionList()
            } else {
                null
            }

        mAppFilterUids = if (mSettings!!.app_filter?.isNotEmpty() == true) {
            val uids = mutableListOf<Int>()

            for (packageName in mSettings!!.app_filter!!) {
                val uid: Int
                try {
                    uid = Utils.getPackageUid(packageManager, packageName, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                    continue
                }

                uids.add(uid)
            }

            uids.toIntArray()
        } else {
            intArrayOf()
        }

        mMalwareDetectionEnabled = Prefs.isMalwareDetectionEnabled(this, mPrefs!!)
        mFirewallEnabled = Prefs.isFirewallEnabled(this, mPrefs!!)

        if (!mSettings!!.root_capture && !mSettings!!.readFromPcap()) {
            Log.i(TAG, "Using DNS server $dns_server")

            // VPN
            val builder = Builder()
                .setMtu(VPN_MTU)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            if (getIPv4Enabled() == 1) {
                builder.addAddress(vpn_ipv4!!, 30)
                    .addRoute("0.0.0.0", 1)
                    .addRoute("128.0.0.0", 1)
                    .addDnsServer(vpn_dns!!)
            }

            if (getIPv6Enabled() == 1) {
                builder.addAddress(VPN_IP6_ADDRESS, 128)

                // Route unicast IPv6 addresses
                builder.addRoute("2000::", 3)
                builder.addRoute("fc00::", 7)

                try {
                    builder.addDnsServer(InetAddress.getByName(Prefs.getDnsServerV6(mPrefs!!)))
                } catch (e: UnknownHostException) {
                    Log.w(TAG, "Could not set IPv6 DNS server")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Invalid IPv6 DNS server address")
                }
            }

            if (mSettings!!.app_filter?.isNotEmpty() == true) {
                Log.d(TAG, "Setting app filter: ${mSettings!!.app_filter}")

                try {
                    for (packageName in mSettings!!.app_filter!!) {
                        builder.addAllowedApplication(packageName)
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    val msg = String.format(
                        resources.getString(R.string.app_not_found),
                        mSettings!!.app_filter
                    )
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    return abortStart()
                }
            } else {
                // VPN exceptions
                val exceptions = mPrefs!!.getStringSet(Prefs.PREF_VPN_EXCEPTIONS, HashSet())
                if (exceptions != null) {
                    for (packageName in exceptions) {
                        try {
                            builder.addDisallowedApplication(packageName)
                        } catch (e: PackageManager.NameNotFoundException) {
                            e.printStackTrace()
                        }
                    }
                }

                if (mSettings!!.tls_decryption) {
                    // Exclude the mitm addon traffic in case system-wide decryption is performed
                    try {
                        builder.addDisallowedApplication(MitmAPI.PACKAGE_NAME)
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace()
                    }
                }
            }

            if (Prefs.isPortMappingEnabled(mPrefs!!)) {
                val portMap = PortMapping(this)
                for (mapping in portMap.iter()) {
                    addPortMapping(
                        mapping.ipproto,
                        mapping.orig_port,
                        mapping.redirect_port,
                        mapping.redirect_ip
                    )
                }
            }

            try {
                mParcelFileDescriptor =
                    builder.setSession(CaptureService.VpnSessionName).establish()
            } catch (e: IllegalArgumentException) {
                Log.d(TAG, "onStartCommand: ${R.string.vpn_setup_failed}")
                return abortStart()
            } catch (e: IllegalStateException) {
                Log.d(TAG, "onStartCommand: ${R.string.vpn_setup_failed}")
                return abortStart()
            }
        }

//        mMalwareWhitelist = PCAPdroid.getInstance().getMalwareWhitelist()
//        mBlacklists = PCAPdroid.getInstance().getBlacklists()
        if (mMalwareDetectionEnabled && !mBlacklists!!.needsUpdate(true)) {
            reloadBlacklists()
        }
        checkBlacklistsUpdates(true)

//        mBlocklist = PCAPdroid.getInstance().getBlocklist()
//        mFirewallWhitelist = PCAPdroid.getInstance().getFirewallWhitelist()

        mConnUpdateThread = Thread({ connUpdateWork() }, "UpdateListener")
        mConnUpdateThread!!.start()

        if (mDumper != null) {
            mDumperThread = Thread({ dumpWork() }, "DumperThread")
            mDumperThread!!.start()
        }

        if (mFirewallEnabled) {
            mNewAppsInstallReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    // executed on the main thread
                    if (Intent.ACTION_PACKAGE_ADDED == intent.action) {
                        val newInstall = !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                        val packageName = intent.data?.schemeSpecificPart ?: return
                        Log.i(
                            TAG,
                            " mNewAppsInstallReceiver onReceive: ${newInstall}, ${packageName}"
                        )
                        if (newInstall && Prefs.blockNewApps(mPrefs!!)) {
                            if (!mBlocklist!!.addApp(packageName)) return

                            mBlocklist!!.save()
                            reloadBlocklist()

                            val app =
                                AppsResolver.resolveInstalledApp(packageManager, packageName, 0)
                            val label = app?.getName() ?: packageName

                            Log.i(
                                TAG,
                                "Blocking newly installed app: $packageName${if (app != null) " - ${app.getUid()}" else ""}"
                            )

                            // ACTIVITY FOR RESULT BACK
//                            val pi = PendingIntent.getActivity(
//                                this@CaptureService,
//                                0,
//                                Intent(this@CaptureService, FirewallActivity::class.java),
//                                Utils.getIntentFlags(0)
//                            )

//                            val unblockIntent = PendingIntent.getBroadcast(
//                                this@CaptureService,
//                                0,
//                                Intent(this@CaptureService, ActionReceiver::class.java)
//                                    .putExtra(ActionReceiver.EXTRA_UNBLOCK_APP, packageName),
//                                Utils.getIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
//                            )

                            // Notify the user
//                            val man = NotificationManagerCompat.from(context)
//                            if (man.areNotificationsEnabled()) {
//                                val notification = NotificationCompat.Builder(
//                                    this@CaptureService,
//                                    NOTIFY_CHAN_OTHER
//                                )
//                                    .setContentIntent(pi)
//                                    .setSmallIcon(R.drawable.ic_logo)
//                                    .setColor(
//                                        ContextCompat.getColor(
//                                            this@CaptureService,
//                                            R.color.colorPrimary
//                                        )
//                                    )
//                                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//                                    .setCategory(NotificationCompat.CATEGORY_STATUS)
//                                    .setContentTitle(getString(R.string.app_blocked))
//                                    .setContentText(getString(R.string.app_blocked_info, label))
//                                    .setAutoCancel(true)
//                                    .addAction(
//                                        R.drawable.ic_check_solid,
//                                        getString(R.string.action_unblock),
//                                        unblockIntent
//                                    )
//                                    .build()
//
//                                man.notify(NOTIFY_ID_APP_BLOCKED, notification)
//                            }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addDataScheme("package")
            }
            registerReceiver(mNewAppsInstallReceiver, filter)
        }

        // Start the native capture thread
        mQueueFull = false
        mCaptureThread = Thread(this, "PacketCapture")
        mCaptureThread!!.start()

        // If the service is killed (e.g. due to low memory), then restart it with a NULL intent
        return START_STICKY
    }

    override fun onRevoke() {
        Log.d(TAG, "onRevoke")
        mRevoked = true
        stopService()
        super.onRevoke()
    }

    override fun run() {
        Log.d("CaptureService", "onRunning")
        if (mSettings!!.root_capture || mSettings!!.readFromPcap()) {
            // Check for INTERACT_ACROSS_USERS, required to query apps of other users/work profiles
            if (mSettings!!.root_capture && checkCallingOrSelfPermission(Utils.INTERACT_ACROSS_USERS) != PackageManager.PERMISSION_GRANTED) {
                val success: Boolean = Utils.rootGrantPermission(this, Utils.INTERACT_ACROSS_USERS)
                mHandler?.post {
                    Log.d(
                        "INTERACT_ACROSS_USERS",
                        "${if (success) R.string.permission_granted else R.string.permission_grant_fail}"
                    )
                }
            }
            runPacketLoop(-1, this, Build.VERSION.SDK_INT)
        } else {
            if (mParcelFileDescriptor != null) {
                val fd = mParcelFileDescriptor!!.fd
                val fdSetsize = getFdSetSize()
                if (fd in 1..<fdSetsize) {
                    Log.d(TAG, "VPN fd: $fd - FD_SETSIZE: $fdSetsize")
                    runPacketLoop(fd, this, Build.VERSION.SDK_INT)

                    // if always-on VPN is stopped, it's not an always-on anymore
                    mIsAlwaysOnVPN = false
                } else Log.e(TAG, "Invalid VPN fd: $fd")
            }
        }

        // After the capture is stopped
        if (mMalwareDetectionEnabled) mBlacklists!!.save()

        // Important: the fd must be closed to properly terminate the VPN
        if (mParcelFileDescriptor != null) {
            try {
                mParcelFileDescriptor!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mParcelFileDescriptor = null
        }

        // NOTE: join the threads here instead in onDestroy to avoid ANR
        stopAndJoinThreads()

        stopService()

        mLock.lock()
        mCaptureThread = null
        mCaptureStopped.signalAll()
        mLock.unlock()

        // Notify
        mHandler?.post {
            updateServiceStatus(ServiceStatus.STOPPED)
//            CaptureCtrl.notifyCaptureStopped(this, getStats())
            Log.d("CaptureCtrl", "Notify Stop ${getStats()}")
        }
    }

    // NOTE: this is only called on low system memory (e.g. obtained via getMemoryInfo). The app
    // may still run out of heap memory, whose monitoring requires polling (see checkAvailableHeap)
    override fun onTrimMemory(level: Int) {
        val lvlStr = trimlvl2str(level)
        val lowMemory = level != TRIM_MEMORY_UI_HIDDEN && level >= TRIM_MEMORY_RUNNING_LOW
        val critical = lowMemory && level >= TRIM_MEMORY_COMPLETE
        Log.d(TAG, "onTrimMemory: $lvlStr - low=$lowMemory, critical=$critical")
        if (critical && !mLowMemory) handleLowMemory()
    }

    override fun protect(socket: Int): Boolean {
        // Do not call protect in root mode
        return if (mSettings!!.root_capture) true else super.protect(socket)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        // Do not nullify INSTANCE to allow its settings and the connections register to be accessible
        // after the capture is stopped
        //INSTANCE = null;
        unregisterNetworkCallbacks()
        if (mBlacklists != null) mBlacklists!!.abortUpdate()
        if (mCaptureThread != null) mCaptureThread!!.interrupt()
        if (mBlacklistsUpdateThread != null) mBlacklistsUpdateThread!!.interrupt()
        if (mNewAppsInstallReceiver != null) {
            unregisterReceiver(mNewAppsInstallReceiver)
            mNewAppsInstallReceiver = null
        }
        super.onDestroy()
    }

    fun setupNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // VPN running notification channel
            var chan = NotificationChannel(
                NOTIFY_CHAN_VPNSERVICE,
                NOTIFY_CHAN_VPNSERVICE, NotificationManager.IMPORTANCE_LOW
            ) // low: no sound
            chan.setShowBadge(false)
            nm.createNotificationChannel(chan)

            // Blacklisted connection notification channel
            chan = NotificationChannel(
                NOTIFY_CHAN_MALWARE_DETECTION,
                getString(R.string.malware_detection), NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(chan)

            // Other notifications
            chan = NotificationChannel(
                NOTIFY_CHAN_OTHER,
                getString(R.string.other_prefs), NotificationManager.IMPORTANCE_DEFAULT
            )
            nm.createNotificationChannel(chan)
        }

        // Status notification builder

        // Status notification builder: Not using now
        val currentActivity = CurrentActivityHolder.currentActivity
        var intent: Intent? = null
        currentActivity?.let {
            intent = Intent(getAppContext(), it::class.java)
        }

        var pi: PendingIntent? = null
        if (intent != null) {
            pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                Utils.getIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }

        mStatusBuilder = NotificationCompat.Builder(this, NOTIFY_CHAN_VPNSERVICE)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setContentIntent(pi!!)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentTitle(resources.getString(R.string.capture_running))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW) // see IMPORTANCE_LOW

        // Malware notification builder
        mMalwareBuilder = NotificationCompat.Builder(this, NOTIFY_CHAN_MALWARE_DETECTION)
            .setSmallIcon(R.drawable.ic_skull)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // see IMPORTANCE_HIGH
    }

    fun getStatusNotification(): Notification {
        val msg = java.lang.String.format(
            getString(R.string.notification_msg),
            Utils.formatBytes(last_bytes), Utils.formatNumber(this, last_connections)
        )
        mStatusBuilder!!.setContentText(msg)
        return mStatusBuilder!!.build()
    }

    fun notifyLowMemory(msg: CharSequence?) {
        val notification: Notification = NotificationCompat.Builder(this, NOTIFY_CHAN_OTHER)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(System.currentTimeMillis())
            .setContentTitle(getString(R.string.low_memory))
            .setContentText(msg)
            .build()
        mHandler?.post {
            Utils.sendImportantNotification(
                this,
                NOTIFY_ID_LOW_MEMORY,
                notification
            )
        }
    }

    fun handleLowMemory() {
        Log.w(TAG, "handleLowMemory called")
        mLowMemory = true
        val fullPayload = getCurPayloadMode() == PayloadMode.FULL
        if (fullPayload) {
            Log.w(TAG, "Disabling full payload")

            // Disable full payload for new connections
            mSettings!!.full_payload = false
            setPayloadMode(PayloadMode.NONE.ordinal)
            if (mSettings!!.tls_decryption) {
                // TLS decryption without payload has little use, stop the capture all together
                stopService()
                notifyLowMemory(getString(R.string.capture_stopped_low_memory))
            } else {
                // Release memory for existing connections
                if (conn_reg != null) {
                    conn_reg!!.releasePayloadMemory()

                    // *possibly* call the gc
                    System.gc()
                    Log.i(
                        TAG, """
     Memory stats full payload release:
     ${Utils.getMemoryStats(this)}
     """.trimIndent()
                    )
                }
                notifyLowMemory(getString(R.string.full_payload_disabled))
            }
        } else {
            // TODO lower memory consumption (e.g. reduce connections register size)
            Log.w(TAG, "low memory detected, expect crashes")
            notifyLowMemory(getString(R.string.low_memory_info))
        }
    }

    fun notifyBlacklistedConnection(conn: ConnectionDescriptor) {
        val uid = conn.uid
        val resolver = AppsResolver(this)
        val app = resolver.getAppByUid(conn.uid, 0) ?: return
        val filter = FilterDescriptor()
        filter.onlyBlacklisted = true

        Log.d(TAG, "Data: \n Filter: ${filter}\n App: ${app.getPackageName()}")

//        // Thinking for send it outside
//        val intent: Intent = Intent(this, MainActivity::class.java)
//            .putExtra("filter", filter)
//            .putExtra("query", app.getPackageName())
//
//        val pi = PendingIntent.getActivity(
//            this, 0,
//            intent, getIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
//        )
//
//        val ruleLabel: String = if (conn.isBlacklistedHost()) {
//            MatchList().getRuleLabel(this, MatchList.RuleType.HOST, conn.info!!)
//        } else {
//            MatchList().getRuleLabel(this, MatchList.RuleType.IP, conn.dstIp)
//        }
//
//        mMalwareBuilder!!
//            .setContentIntent(pi)
//            .setWhen(System.currentTimeMillis())
//            .setContentTitle(
//                String.format(
//                    resources.getString(R.string.malicious_connection_app),
//                    app.getName()
//                )
//            )
//            .setContentText(ruleLabel)
//        val notification = mMalwareBuilder!!.build()
//
//        // Use the UID as the notification ID to group alerts from the same app
//        mHandler?.post {
//            Utils.sendImportantNotification(this, uid, notification)
//        }
    }

    fun registerNetworkCallbacks() {
        if (mNetworkCallback != null) return
        val fallbackDns = Prefs.getDnsServerV4(mPrefs!!)
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        mNetworkCallback = object : NetworkCallback() {
            override fun onLost(network: Network) {
                Log.d(TAG, "onLost $network")

                // If the network goes offline we roll back to the fallback DNS server to
                // avoid possibly using a private IP DNS server not reachable anymore
                if (network.getNetworkHandle() == mMonitoredNetwork) {
                    Log.i(
                        TAG,
                        "Main network $network lost, using fallback DNS $fallbackDns"
                    )
                    dns_server = fallbackDns
                    mMonitoredNetwork = 0
                    unregisterNetworkCallbacks()

                    // change native
                    setDnsServer(dns_server!!)
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                Log.d(TAG, "onLinkPropertiesChanged $network")
                if (network.getNetworkHandle() == mMonitoredNetwork) handleLinkProperties(
                    linkProperties
                )
            }
        }
        try {
            Log.d(TAG, "registerNetworkCallback")
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                mNetworkCallback!!
            )
        } catch (e: SecurityException) {
            // this is a bug in Android 11 - https://issuetracker.google.com/issues/175055271?pli=1
            e.printStackTrace()
            Log.w(TAG, "registerNetworkCallback failed, DNS server detection disabled")
            dns_server = fallbackDns
            mNetworkCallback = null
        }
    }

    fun unregisterNetworkCallbacks() {
        if (mNetworkCallback != null) {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                Log.d(TAG, "unregisterNetworkCallback")
                cm.unregisterNetworkCallback(mNetworkCallback!!)
            } catch (e: java.lang.IllegalArgumentException) {
                Log.w(TAG, "unregisterNetworkCallback failed: $e")
            }
            mNetworkCallback = null
        }
    }

    fun handleLinkProperties(linkProperties: LinkProperties?) {
        if (linkProperties == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mPrivateDnsMode = Utils.getPrivateDnsMode(linkProperties)
            Log.i(TAG, "Private DNS: $mPrivateDnsMode")
            if (mSettings!!.readFromPcap()) {
                mDnsEncrypted = false
                setPrivateDnsBlocked(false)
            } else if (!mSettings!!.root_capture && mSettings!!.auto_block_private_dns) {
                mDnsEncrypted = mPrivateDnsMode == PrivateDnsMode.STRICT
                val opportunisticMode = mPrivateDnsMode == PrivateDnsMode.OPPORTUNISTIC

                /* Private DNS can be in one of these modes:
                 *  1. Off
                 *  2. Automatic (default): also called "opportunistic", only use it if not blocked
                 *  3. Strict: private DNS is enforced, Internet unavailable if blocked. User must set a specific DNS server.
                 * When in opportunistic mode, PCAPdroid will block private DNS connections to force the use of plain-text
                 * DNS queries, which can be extracted by PCAPdroid. */if (mBlockPrivateDns != opportunisticMode) {
                    mBlockPrivateDns = opportunisticMode
                    setPrivateDnsBlocked(mBlockPrivateDns)
                }
            } else {
                // in root capture we don't block private DNS requests in opportunistic mode
                mDnsEncrypted = mPrivateDnsMode != PrivateDnsMode.DISABLED
                setPrivateDnsBlocked(false)
            }
            if (mDnsEncrypted && !mStrictDnsNoticeShown) {
                mStrictDnsNoticeShown = true
                showToastLong(this, R.string.private_dns_message_notice)
            }
        }
    }

    fun signalServicesTermination() {
        mPendingUpdates.offer(Pair(null, null))
        stopPcapDump()
    }

    fun checkBlacklistsUpdates(firstUpdate: Boolean) {
        if (!mMalwareDetectionEnabled || mBlacklistsUpdateThread != null) return
        if (mBlacklistsUpdateRequested || mBlacklists!!.needsUpdate(firstUpdate)) {
            mBlacklistsUpdateThread = Thread({ updateBlacklistsWork() }, "Blacklists Update")
            mBlacklistsUpdateThread!!.start()
        }
    }

    fun updateNotification() {
        if (mStopping) return
        val notification = getStatusNotification()
        NotificationManagerCompat.from(this).notify(NOTIFY_ID_VPNSERVICE, notification)
    }

    fun updateBlacklistsWork() {
        mBlacklistsUpdateRequested = false
        mBlacklists!!.update()
        reloadBlacklists()
        mBlacklistsUpdateThread = null
    }

    fun updateServiceStatus(curStatus: ServiceStatus) {
        // notify the observers
        // NOTE: new subscribers will receive the STOPPED status right after their registration
        serviceStatus.postValue(curStatus)
        if (curStatus == ServiceStatus.STARTED) {
            if (mMalwareDetectionEnabled) reloadMalwareWhitelist()
            if (mDecryptionList != null) reloadDecryptionList()

            Log.i(TAG, "updateServiceStatus: Here")
            reloadBlocklist()
            reloadFirewallWhitelist()
        } else if (curStatus == ServiceStatus.STOPPED) {
            if (mRevoked && Prefs.restartOnDisconnect(mPrefs!!) && !mIsAlwaysOnVPN && isVpnCapture() == 1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.i(TAG, "VPN disconnected, starting reconnect service")
                    val intent = Intent(
                        this,
                        VpnReconnectService::class.java
                    )
                    ContextCompat.startForegroundService(this, intent)
                }
            }
        }
    }

    fun getIfname(ifidx: Int): String? {
        if (ifidx <= 0) return ""
        var rv = mIfIndexToName!![ifidx]
        if (rv != null) return rv

        // Not found, try to retrieve it
        var iface: NetworkInterface? = null
        try {
            iface = NetworkInterface.getByIndex(ifidx)
        } catch (ignored: SocketException) {
        }
        rv = if (iface != null) iface.name else ""

        // store it even if not found, to avoid looking up it again
        mIfIndexToName!!.put(ifidx, rv)
        return rv
    }

    fun getInterfaceName(ifidx: Int): String {
        var ifname: String? = null
        if (INSTANCE != null) ifname = INSTANCE!!.getIfname(ifidx)
        return ifname ?: ""
    }

    fun connUpdateWork() {
        while (true) {
            var item: Pair<Array<ConnectionDescriptor?>?, Array<ConnectionUpdate?>?>
            try {
                item = mPendingUpdates.take()
            } catch (e: InterruptedException) {
                continue
            }

            if (item.first == null) { // termination request
                Log.i(TAG, "Connection update thread exit requested")
                break
            }
            val newConnections = item.first
            val connectionsUpdates = item.second
            checkBlacklistsUpdates(false)

            if (mBlocklist?.checkGracePeriods() == true) {
                mHandler?.post { reloadBlocklist() }
            }

            if (!mLowMemory) {
                checkAvailableHeap()
            }

            // synchronize the conn_reg to ensure that newConnections and connectionsUpdates run atomically
            // thus preventing the ConnectionsAdapter from interleaving other operations
//            Log.i(
//                TAG,
//                "Synchronize the conn_reg to ensure that newConnections and connectionsUpdates run atomically"
//            )

            synchronized(conn_reg!!) {

                if (!newConnections.isNullOrEmpty()) {
//                    Log.i(TAG, "newConnections:\n${newConnections.joinToString("\n ")}}")
                    conn_reg!!.newConnections(newConnections.filterNotNull().toTypedArray())
                }

                if (!connectionsUpdates.isNullOrEmpty()) {
//                    Log.i(TAG, "updateConnections:\n${connectionsUpdates.joinToString("\n ")}")
                    conn_reg!!.connectionsUpdates(connectionsUpdates)
                }
            }
        }
    }

    fun dumpWork() {
        while (true) {
            val data: ByteArray = try {
                mDumpQueue!!.take()
            } catch (e: InterruptedException) {
                continue
            }
            if (data.isEmpty()) // termination request
                break
            try {
                mDumper!!.dumpData(data)
            } catch (e: IOException) {
                // Stop the capture
                e.printStackTrace()
                reportError(e.localizedMessage)
                mHandler?.post { stopPacketLoop() }
                break
            }
        }
        try {
            mDumper!!.stopDumper()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopAndJoinThreads() {
        signalServicesTermination()
        Log.d(TAG, "Joining threads...")
        while (mConnUpdateThread != null && mConnUpdateThread!!.isAlive) {
            try {
                Log.d(TAG, "Joining conn update thread...")
                mConnUpdateThread!!.join()
            } catch (ignored: InterruptedException) {
                mPendingUpdates.offer(Pair(null, null))
            }
        }
        mConnUpdateThread = null
        while (mDumperThread != null && mDumperThread!!.isAlive) {
            try {
                Log.d(TAG, "Joining dumper thread...")
                mDumperThread!!.join()
            } catch (ignored: InterruptedException) {
                stopPcapDump()
            }
        }
        mDumperThread = null
        mDumper = null
        if (mMitmReceiver != null) {
            try {
                mMitmReceiver!!.stop()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            mMitmReceiver = null
        }
    }

    fun reportError(msg: String?) {
        HAS_ERROR = true
        mHandler?.post {
            var err = msg
            when (msg) {
                "Invalid PCAP file" -> err = getString(R.string.invalid_pcap_file)
                "Could not open the capture interface" -> err =
                    getString(R.string.capture_interface_open_error)

                "Unsupported datalink" -> err = getString(R.string.unsupported_pcap_datalink)
                "The specified PCAP file does not exist" -> err =
                    getString(R.string.pcap_file_not_exists)

                "pcapd daemon start failure" -> if (mSettings!!.root_capture) err =
                    getString(R.string.root_capture_pcapd_start_failure)

                "pcapd daemon did not spawn" -> if (mSettings!!.root_capture) err =
                    getString(R.string.root_capture_start_failed)

                "PCAP read error" -> err = getString(R.string.pcap_read_error)
            }
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
        }
    }

    fun reloadBlocklist() {
        Log.i(TAG, "reloadBlocklist")
        if (mBilling?.isFirewallVisible() == true) {
            Log.i(TAG, "reloading firewall blocklist")
            reloadBlocklist(mBlocklist!!.toListDescriptor())
        }
    }

    fun reloadFirewallWhitelist() {
        if (!mBilling!!.isFirewallVisible()) return
        Log.i(TAG, "reloading firewall whitelist")
        reloadFirewallWhitelist((if (Prefs.isFirewallWhitelistMode(mPrefs!!)) mFirewallWhitelist!!.toListDescriptor() else null)!!)
    }

    fun stopPcapDump() {
        if (mDumpQueue != null && mDumperThread != null && mDumperThread!!.isAlive) mDumpQueue!!.offer(
            ByteArray(0)
        )
    }

    fun getDnsServer(): String {
        return dns_server!!
    }

    fun getStats(): CaptureStats {
        val stats = lastStats.getValue()
        return stats ?: CaptureStats()
    }

    fun getIPv4Enabled(): Int {
        return if (mSettings!!.ip_mode != Prefs.IpMode.IPV6_ONLY) 1 else 0
    }

    fun getIPv6Enabled(): Int {
        return if (mSettings!!.ip_mode != Prefs.IpMode.IPV4_ONLY) 1 else 0
    }

    fun getAppFilter(): Set<String>? {
        return if (INSTANCE != null) INSTANCE!!.mSettings!!.app_filter else null
    }

    fun getPcapUri(): Uri? {
        return if (INSTANCE != null) INSTANCE!!.mPcapUri else null
    }

    fun getPcapFname(): String? {
        return if (INSTANCE != null) INSTANCE!!.mPcapFname else null
    }


    fun getBytes(): Long {
        return if (INSTANCE != null) INSTANCE!!.last_bytes else 0
    }

    fun getCollectorAddress(): String {
        return if (INSTANCE != null) INSTANCE!!.mSettings!!.collector_address else ""
    }

    fun getCollectorPort(): Int {
        return if (INSTANCE != null) INSTANCE!!.mSettings!!.collector_port else 0
    }

    fun getHTTPServerPort(): Int {
        return if (INSTANCE != null) INSTANCE!!.mSettings!!.http_server_port else 0
    }


    fun getDNSServer(): String {
        return if (INSTANCE != null) INSTANCE!!.getDnsServer() else ""
    }

    fun isRootCapture(): Int {
        return if (mSettings!!.root_capture) 1 else 0
    }

    fun isPcapFileCapture(): Int {
        return if (mSettings!!.readFromPcap()) 1 else 0
    }

    fun isTlsDecryptionEnabled(): Int {
        return if (mSettings!!.tls_decryption) 1 else 0
    }

    fun isCapturingAsRoot(): Boolean {
        return INSTANCE != null && INSTANCE!!.isRootCapture() == 1
    }

    fun isReadingFromPcapFile(): Boolean {
        return INSTANCE != null && INSTANCE!!.isPcapFileCapture() == 1
    }

    fun isDNSEncrypted(): Boolean {
        return INSTANCE != null && INSTANCE!!.mDnsEncrypted
    }


    fun isDecryptingTLS(): Boolean {
        return INSTANCE != null && INSTANCE!!.isTlsDecryptionEnabled() == 1
    }


    fun isIPv6Enabled(): Boolean {
        return INSTANCE != null && INSTANCE!!.getIPv6Enabled() == 1
    }


    fun isDecryptionListEnabled(): Boolean {
        return INSTANCE != null && INSTANCE!!.mDecryptionList != null
    }


    fun getMitmProxyStatus(): MitmReceiver.Status? {
        return if (INSTANCE == null || INSTANCE!!.mMitmReceiver == null) MitmReceiver.Status.NOT_STARTED else INSTANCE!!.mMitmReceiver?.getProxyStatus()
    }

    fun isAlwaysOnVPN(): Boolean {
        return INSTANCE != null && INSTANCE!!.mIsAlwaysOnVPN
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    fun isLockdownVPN(): Boolean {
        return INSTANCE != null && INSTANCE!!.isLockdownEnabled
    }

    fun isUserDefinedPcapUri(): Boolean {
        return INSTANCE == null || INSTANCE!!.mSettings!!.pcap_uri.isNotEmpty()
    }


    fun requestBlacklistsUpdate() {
        if (INSTANCE != null) {
            INSTANCE!!.mBlacklistsUpdateRequested = true

            // Wake the update thread to run the blacklist thread
            INSTANCE!!.mPendingUpdates.offer(Pair(arrayOfNulls(0), arrayOfNulls(0)))
        }
    }

    /* The following methods are called from native code */

    /* The following methods are called from native code */
    fun getVpnIPv4(): String {
        return vpn_ipv4!!
    }

    fun getVpnDns(): String {
        return vpn_dns!!
    }


    fun getIpv6DnsServer(): String? {
        return Prefs.getDnsServerV6(mPrefs!!)
    }

    fun getSocks5Enabled(): Int {
        return if (mSocks5Enabled) 1 else 0
    }

    fun getSocks5ProxyAddress(): String {
        return mSocks5Address!!
    }

    fun getSocks5ProxyPort(): Int {
        return mSocks5Port
    }

    fun getSocks5ProxyAuth(): String? {
        return mSocks5Auth
    }

    fun malwareDetectionEnabled(): Int {
        return if (mMalwareDetectionEnabled) 1 else 0
    }

    fun firewallEnabled(): Int {
        return if (mFirewallEnabled) 1 else 0
    }

    fun addPcapdroidTrailer(): Int {
        return if (mSettings!!.pcapdroid_trailer) 1 else 0
    }

    fun isPcapngEnabled(): Int {
        return if (mSettings!!.pcapng_format) 1 else 0
    }

    fun getAppFilterUids(): IntArray {
        return mAppFilterUids!!
    }

    fun getMitmAddonUid(): Int {
        return getUid(this)
    }

    fun getCaptureInterface(): String {
        return mSettings!!.capture_interface
    }

    fun getSnaplen(): Int {
        return mSettings!!.snaplen
    }

    fun getMaxPktsPerFlow(): Int {
        return mSettings!!.max_pkts_per_flow
    }

    fun getMaxDumpSize(): Int {
        return mSettings!!.max_dump_size
    }

    fun getPayloadMode(): Int {
        return getCurPayloadMode().ordinal
    }

    fun getVpnMTU(): Int {
        return VPN_MTU
    }

    fun getBlockQuickMode(): Int {
        return mSettings!!.block_quic_mode.ordinal
    }

    // returns 1 if dumpPcapData should be called
    fun pcapDumpEnabled(): Int {
        return if (mSettings!!.dump_mode != DumpMode.NONE) 1 else 0
    }

    fun getPcapDumperBpf(): String? {
        return if (mDumper != null) mDumper!!.getBpf() else ""
    }

    // from NetGuard
    @TargetApi(Build.VERSION_CODES.Q)
    fun getUidQ(protocol: Int, saddr: String?, sport: Int, daddr: String?, dport: Int): Int {
        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */) return Utils.UID_UNKNOWN

        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val local = InetSocketAddress(saddr, sport)
        val remote = InetSocketAddress(daddr, dport)
        Log.d(TAG, "Get uid local=$local remote=$remote")
        return cm.getConnectionOwnerUid(protocol, local, remote)
    }

    fun updateConnections(
        newConnections: Array<ConnectionDescriptor?>?,
        connectionsUpdate: Array<ConnectionUpdate?>?
    ) {
        if (mQueueFull) // if the queue is full, stop receiving updates to avoid inconsistent incr_ids
            return

        // Put the update into a queue to avoid performing much work on the capture thread.
        // This will be processed by mConnUpdateThread.
        if (!mPendingUpdates.offer(Pair(newConnections, connectionsUpdate))) {
            Log.e(TAG, "The updates queue is full, this should never happen!")
            mQueueFull = true
            mHandler?.post { stopPacketLoop() }
        }
    }

    fun waitForCaptureStop() {
        if (INSTANCE == null) return
        Log.d(TAG, "waitForCaptureStop " + Thread.currentThread().name)
        INSTANCE!!.mLock.lock()
        try {
            while (INSTANCE!!.mCaptureThread != null) {
                try {
                    INSTANCE!!.mCaptureStopped.await()
                } catch (ignored: InterruptedException) {
                }
            }
        } finally {
            INSTANCE!!.mLock.unlock()
        }
        Log.d(TAG, "waitForCaptureStop done " + Thread.currentThread().name)
    }

    fun hasError(): Boolean {
        return HAS_ERROR
    }

    // called from native
    fun sendStatsDump(stats: CaptureStats) {
        //Log.d(TAG, "sendStatsDump");
        last_bytes = stats.bytes_sent + stats.bytes_rcvd
        last_connections = stats.tot_conns
        mHandler?.post { updateNotification() }

        // notify the observers
        lastStats.postValue(stats)
    }

    // called from native
    private fun sendServiceStatus(curStatus: String) {
        updateServiceStatus(if (curStatus == "started") ServiceStatus.STARTED else ServiceStatus.STOPPED)
    }

    /* Exports a PCAP data chunk */
    fun dumpPcapData(data: ByteArray) {
        if (mDumper != null && data.isNotEmpty()) {
            while (true) {
                try {
                    // wait until the queue has space to insert the data. If the queue is full, we
                    // will experience slow-downs/drops but this is expected
                    mDumpQueue!!.put(data)
                    break
                } catch (e: InterruptedException) {
                    // retry
                }
            }
        }
    }

    fun setFirewallEnabled(enabled: Boolean) {
        if (INSTANCE == null) return
        INSTANCE!!.mFirewallEnabled = enabled
        nativeSetFirewallEnabled(enabled)
    }


    fun observeStats(lifecycleOwner: LifecycleOwner?, observer: Observer<CaptureStats?>?) {
        lastStats.observe(lifecycleOwner!!, observer!!)
    }

    fun observeStatus(lifecycleOwner: LifecycleOwner?, observer: Observer<ServiceStatus?>?) {
        serviceStatus.observe(lifecycleOwner!!, observer!!)
    }

    fun getPlatformInfo() {
        initPlatformInfo(APP_VERSION, DEVICE_MODEL, OS_VERSION)
    }

    fun getWorkingDir(): String {
        return cacheDir.absolutePath
    }

    fun getPersistentDir(): String {
        return filesDir.absolutePath
    }

    fun getLibprogPath(progName: String): String {
        // executable binaries are stored into the /lib folder of the app
        val dir = applicationInfo.nativeLibraryDir
        return "$dir/lib$progName.so"
    }

    fun getPrivateDnsMode(): PrivateDnsMode? {
        return if (isServiceActive()) INSTANCE!!.mPrivateDnsMode else null
    }

    // NOTE: to be invoked only by the native code
    fun getApplicationByUid(uid: Int): String {
        val dsc = nativeAppsResolver!!.getAppByUid(uid, 0) ?: return ""
        return dsc.getName()
    }

    fun getBlacklistsInfo(): Array<BlacklistDescriptor?> {
        val blsinfo = arrayOfNulls<BlacklistDescriptor>(
            mBlacklists!!.getNumBlacklists()
        )
        var i = 0
        val it = mBlacklists!!.iter()
        while (it.hasNext()) blsinfo[i++] = it.next()
        return blsinfo
    }

    fun notifyBlacklistsLoaded(loadedBlacklists: Array<NativeBlacklistStatus?>?) {
        // this is invoked from the packet capture thread. Use the handler to save time.
        mHandler?.post {
            if (loadedBlacklists != null) {
                mBlacklists?.onNativeLoaded(loadedBlacklists)
            }
        }
    }
}