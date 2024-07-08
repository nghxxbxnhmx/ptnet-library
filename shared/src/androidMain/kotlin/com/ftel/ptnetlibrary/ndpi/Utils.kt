package com.ftel.ptnetlibrary.ndpi

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Notification
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.models.AppDescriptor
import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor
import com.ftel.ptnetlibrary.ndpi.models.Prefs
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.io.*
import java.math.BigInteger
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

import com.ftel.ptnetlibrary.R
import com.ftel.ptnetlibrary.BuildConfig

@Suppress("UNCHECKED_CAST")
@SuppressLint("QueryPermissionsNeeded", "ObsoleteSdkInt", "MissingPermission")
object Utils {
    val TAG = "Utils"
    val INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS"
    val PCAPDROID_WEBSITE = "https://pcapdroid.org"
    val PER_USER_RANGE = 100000
    val UID_UNKNOWN = -1
    val UID_NO_FILTER = -2
    val LOW_HEAP_THRESHOLD = 10485760 /* 10 MB */
    private var rootAvailable: Boolean? = null
    private var primaryLocale: Locale? = null
    private val l7Protocols: Array<String>? = null

    enum class BuildType {
        UNKNOWN,
        DEBUG,
        GITHUB,        // Github release
        FDROID,       // F-droid release
        PLAYSTORE     // Google play release
    }


    enum class PrivateDnsMode {
        DISABLED,
        OPPORTUNISTIC,
        STRICT;

        override fun toString(): String {
            return super.toString().lowercase(Locale.getDefault())
        }
    }

    fun getAppVersionString(): String {
        return "PCAPdroid v" + BuildConfig.VERSION_NAME
    }

    fun getDeviceModel(): String {
        return if (Build.MODEL.startsWith(Build.MANUFACTURER)) Build.MANUFACTURER else Build.MANUFACTURER + " " + Build.MODEL
    }

    fun getOsVersion(): String {
        return "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"
    }

    // Get Info
    fun getUserId(uid: Int): Int {
        return uid / PER_USER_RANGE
    }

    fun getCountryName(context: Context, countryCode: String): String {
        val curLocale: Locale = getPrimaryLocale(context)
        return Locale(curLocale.country, countryCode).displayCountry
    }

    fun getSecondLevelDomain(domain: String): String {
        val tldPos = domain.lastIndexOf(".")
        if (tldPos <= 0) return domain
        val rootPos = domain.substring(0, tldPos).lastIndexOf(".")
        return if (rootPos <= 0) domain else domain.substring(rootPos + 1)
    }

    fun getLocalizedConfig(context: Context): Configuration {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val config = context.resources.configuration
        if (!Prefs.useEnglishLanguage(prefs)) return config
        val locale = Locale("en")
        Locale.setDefault(locale)
        config.setLocale(locale)
        return config
    }

    private fun getPrimaryLocale(context: Context): Locale {
        if (primaryLocale == null) {
            val config: Configuration = context.resources.configuration
            primaryLocale =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.getLocales()
                    .get(0) else config.locale
        }
        return primaryLocale!!
    }

    /* Detects and returns the end of the HTTP request/response headers. 0 is returned if not found. */
    fun getEndOfHTTPHeaders(buf: ByteArray): Int {
        for (i in 0..buf.size - 4) {
            if (buf[i] == '\r'.code.toByte() && buf[i + 1] == '\n'.code.toByte() && buf[i + 2] == '\r'.code.toByte() && buf[i + 3] == '\n'.code.toByte()) return i + 4
        }
        return 0
    }

    fun getIntentFlags(flags: Int): Int {
        var inflags = flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            inflags = inflags or PendingIntent.FLAG_IMMUTABLE
        return inflags
    }

    fun getInstalledPackages(pm: PackageManager, flags: Int): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getInstalledPackages(
            PackageManager.PackageInfoFlags.of(flags.toLong())
        ) else pm.getInstalledPackages(flags)
    }

    fun getRunningVpn(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            val networks: Array<Network> = cm.allNetworks
            for (net in networks) {
                val cap = cm.getNetworkCapabilities(net)
                if (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    Log.d("hasVPNRunning", "detected VPN connection: $net")
                    return net
                }
            }
        } catch (e: SecurityException) {
            // this is a bug in Android 11 - https://issuetracker.google.com/issues/175055271?pli=1
            e.printStackTrace()
        }
        return null
    }

    fun <T : Serializable?> getSerializableExtra(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(key, clazz)
        } else {
            try {
                intent.getSerializableExtra(key) as T?
            } catch (unused: ClassCastException) {
                null
            }
        }
    }

    fun getNetworkInterfaces(): Enumeration<NetworkInterface> {
        try {
            val ifs: Enumeration<NetworkInterface>? = NetworkInterface.getNetworkInterfaces()
            if (ifs != null) {
                return ifs
            }
        } catch (e: SocketException) {
            // Handle SocketException here
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // Handle NullPointerException here
            e.printStackTrace()
        }

        return Collections.enumeration(emptyList())
    }

    fun isLocalNetworkAddress(checkAddress: InetAddress?): Boolean {
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isVirtual) {
                    val addrs = intf.getInterfaceAddresses()
                    for (addr in addrs) {
                        if (subnetContains(
                                addr.address,
                                addr.networkPrefixLength.toInt(),
                                checkAddress!!
                            )
                        ) return true
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun isLocalNetworkAddress(checkAddress: String?): Boolean {
        // this check is necessary as otherwise host resolution would be triggered on the main thread
        return if (!validateIpAddress(checkAddress)) false else try {
            isLocalNetworkAddress(InetAddress.getByName(checkAddress))
        } catch (ignored: UnknownHostException) {
            false
        }
    }

    fun isCAInstalled(caCert: X509Certificate?): Boolean {
        try {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null, null)
            return ks.getCertificateAlias(caCert) != null
        } catch (e: KeyStoreException) {
            e.printStackTrace()
            return false
        } catch (e: CertificateException) {
            e.printStackTrace()
            return false
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return false
        }
    }

    fun isCAInstalled(ca_pem: String?): Boolean {
        if (ca_pem == null) return false

        val caCert: X509Certificate = x509FromPem(ca_pem) ?: return false

        return isCAInstalled(caCert)
    }

    fun x509FromPem(pem: String): X509Certificate? {
        val begin = pem.indexOf('\n') + 1
        val end = pem.indexOf('-', begin)

        if ((begin > 0) && (end > begin)) {
            val cert64 = pem.substring(begin, end)
            //Log.d(TAG, "Cert: " + cert64);
            try {
                val cf = CertificateFactory.getInstance("X.509")
                val certData = Base64.decode(cert64, Base64.DEFAULT)
                return cf.generateCertificate(ByteArrayInputStream(certData)) as X509Certificate
            } catch (e: CertificateException) {
                e.printStackTrace()
            }
        }

        return null
    }

    private val IPV4_PATTERN: Pattern = Pattern.compile(
        "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$"
    )

    fun validateIpv4Address(s: String?): Boolean {
        val matcher = IPV4_PATTERN.matcher(s!!)
        return matcher.matches()
    }

    fun isValidIPv6(address: String?): Boolean {
        if (address.isNullOrEmpty()) return false
        val firstChar = address[0]
        if (firstChar != ':' && (firstChar.digitToIntOrNull(16) ?: -1) < 0) return false
        var segmentCount = 0
        val temp = "$address:"
        var doubleColonFound = false
        var pos = 0
        while (pos < temp.length) {
            val end = temp.indexOf(':', pos)
            if (end < pos) break
            if (segmentCount == 8) return false

            if (pos != end) {
                val value = temp.substring(pos, end)

                if (end == temp.length - 1 && value.contains('.')) {
                    // add an extra one as address covers 2 words.
                    if (++segmentCount == 8) return false
                    if (!validateIpv4Address(value)) return false
                } else if (!isParseableIPv6Segment(temp, pos, end)) return false
            } else {
                if (end != 1 && end != temp.length - 1 && doubleColonFound) return false
                doubleColonFound = true
            }

            pos = end + 1
            ++segmentCount
        }
        return segmentCount == 8 || doubleColonFound
    }

    private fun isParseableIPv6Segment(s: String, pos: Int, end: Int): Boolean {
        return isParseable(s, pos, end, 16, 4, true, 0x0000, 0xFFFF)
    }

    private fun isParseable(
        s: String, pos: Int, end: Int, radix: Int,
        maxLength: Int, allowLeadingZero: Boolean,
        minValue: Int, maxValue: Int
    ): Boolean {
        var pos = pos
        val length = end - pos
        if ((length < 1) or (length > maxLength)) return false
        val checkLeadingZero = (length > 1) and !allowLeadingZero
        if (checkLeadingZero && (s[pos].digitToIntOrNull(radix) ?: -1) <= 0) return false
        var value = 0
        while (pos < end) {
            val c = s[pos++]
            val d = c.digitToIntOrNull(radix) ?: -1
            if (d < 0) {
                return false
            }
            value *= radix
            value += d
        }
        return (value >= minValue) and (value <= maxValue)
    }


    private val IP_ADDRESS: Pattern = Pattern.compile(
        "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                + "|[1-9][0-9]|[0-9]))"
    )

    fun validateIpAddress(value: String?): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) InetAddresses.isNumericAddress(
            value!!
        ) else {
            val matcher = IP_ADDRESS.matcher(value!!)
            matcher.matches()
        }
    }

    fun subnetContains(subnet: InetAddress, prefix: Int, address: InetAddress): Boolean {
        val addrlen = subnet.address.size
        val maskBuf = ByteBuffer.allocate(addrlen)
        for (i in 0 until addrlen / 4) maskBuf.putInt(-1)

        // 0xFFFFF...0000000
        val mask = BigInteger(1, maskBuf.array()).shiftRight(prefix).not()
        val start = BigInteger(1, subnet.address).and(mask)
        val end = start.add(mask.not())
        val toCheck = BigInteger(1, address.address)
        return toCheck.compareTo(start) >= 0 && toCheck.compareTo(end) <= 0
    }

    fun getDnsServer(cm: ConnectivityManager, net: Network?): String? {
        val props = cm.getLinkProperties(net)
        if (props != null) {
            val dnsServers = props.dnsServers
            for (addr in dnsServers) {
                // Get the first IPv4 DNS server
                if (addr is Inet4Address) {
                    return addr.getHostAddress()
                }
            }
        }
        return null
    }

    fun getUniqueFileName(context: Context?, ext: String): String {
        val locale = getPrimaryLocale(context!!)
        val fmt: DateFormat = SimpleDateFormat("dd_MMM_HH_mm_ss", locale)
        return ("PCAPdroid_" + fmt.format(Date())).toString() + "." + ext
    }

    fun getUniquePcapFileName(context: Context?, pcapng_format: Boolean): String {
        return getUniqueFileName(context, if (pcapng_format) "pcapng" else "pcap")
    }

    // Get a URI to write a file into the downloads folder, into a folder named "PCAPdroid"
    // If the file exists, it's overwritten
    fun getDownloadsUri(context: Context, fname: String): Uri? {
        val values = ContentValues()

        //values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fname)
        var selectQuery = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android Q+ cannot directly access the external dir. Must use RELATIVE_PATH instead.
            // Important: trailing "/" required for the selectQuery
            val relPath = Environment.DIRECTORY_DOWNLOADS + "/PCAPdroid/"
            selectQuery = MediaStore.MediaColumns.RELATIVE_PATH + "='" + relPath + "' AND " +
                    MediaStore.MediaColumns.DISPLAY_NAME + "='" + fname + "'"
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    showToastLong(context, R.string.external_storage_perm_required)
                    return null
                }
            }

            // NOTE: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) returns an app internal folder
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var folder = File("$downloadsDir/PCAPdroid")
            try {
                folder.mkdirs()
            } catch (ignored: java.lang.Exception) {
            }
            if (!folder.exists()) folder = downloadsDir
            val path = "$folder/$fname"
            Log.d(TAG, "getDownloadsUri: path=$path")
            selectQuery = MediaStore.MediaColumns.DATA + "='" + path + "'"
            values.put(MediaStore.MediaColumns.DATA, path)
        }
        val externalUri = MediaStore.Files.getContentUri("external")

        // if the file with given name already exists, overwrite it
        try {
            context.contentResolver.query(
                externalUri,
                arrayOf(MediaStore.MediaColumns._ID),
                selectQuery,
                null,
                null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val id =
                        cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val existingUri = ContentUris.withAppendedId(externalUri, id)
                    Log.d(TAG, "getDownloadsUri: overwriting file $existingUri")
                    return existingUri
                }
            }
        } catch (ignored: java.lang.Exception) {
        }
        return try {
            val newUri = context.contentResolver.insert(externalUri, values)
            Log.d(TAG, "getDownloadsUri: new file $newUri")
            newUri
        } catch (e: java.lang.Exception) {
            // On some devices, it may trigger "IllegalArgumentException: Volume external_primary not found"
            Log.e(TAG, "getDownloadsUri failed:" + e.message)
            showToastLong(context, R.string.write_ext_storage_failed)
            null
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    fun getPrivateDnsMode(linkProperties: LinkProperties): PrivateDnsMode {
        return if (linkProperties.privateDnsServerName != null) PrivateDnsMode.STRICT else if (linkProperties.isPrivateDnsActive) PrivateDnsMode.OPPORTUNISTIC else PrivateDnsMode.DISABLED
    }

    // --------------------------------------- CHECK ---------------------------------------------//
    fun isRootAvailable(): Boolean {
        if (rootAvailable == null) {
            val path = System.getenv("PATH")
            rootAvailable = false
            if (path != null) {
                Log.d("isRootAvailable", "PATH = $path")
                for (part in path.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    val f = File("$part/su")
                    if (f.exists()) {
                        Log.d("isRootAvailable", "'su' binary found at " + f.absolutePath)
                        rootAvailable = true
                        break
                    }
                }
            }
        }
        return rootAvailable!!
    }

    fun ungzip(`is`: InputStream?, dst: String?): Boolean {
        try {
            GZIPInputStream(`is`).use { gis ->
                BufferedOutputStream(FileOutputStream(dst)).use { bos ->
                    val bytesIn = ByteArray(4096)
                    var read: Int
                    while (gis.read(bytesIn).also { read = it } != -1) bos.write(bytesIn, 0, read)
                }
                return true
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    @Throws(PackageManager.NameNotFoundException::class)
    fun getPackageInfo(pm: PackageManager, packageName: String?, flags: Int): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageInfo(
            packageName!!, PackageManager.PackageInfoFlags.of(flags.toLong())
        ) else pm.getPackageInfo(
            packageName!!, flags
        )
    }

    @Throws(PackageManager.NameNotFoundException::class)
    fun getPackageUid(pm: PackageManager, packageName: String?, flags: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageUid(
            packageName!!, PackageManager.PackageInfoFlags.of(flags.toLong())
        ) else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) pm.getPackageUid(
            packageName!!, 0
        ) else pm.getApplicationInfo(packageName!!, 0).uid
    }

    private fun getPCADroidUid(context: Context): Int {
        // NOTE: when called from a work profile, it correctly returns the work profile UID
        val app =
            AppsResolver.resolveInstalledApp(context.packageManager, BuildConfig.APPLICATION_ID, 0)
        return app?.getUid() ?: UID_UNKNOWN
    }

    fun getMemoryStats(context: Context): String {
        // This accounts system-wide limits
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val memState = RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(memState)

        // This accounts app-specific limits (dalvik heap)
        val runtime = Runtime.getRuntime()
        val heapAvailable: Long = getAvailableHeap()
        val heapLow = heapAvailable <= LOW_HEAP_THRESHOLD
        return ((("""${
            ((("[Runtime] free: " + formatBytes(runtime.freeMemory())).toString() + ", max: " + formatBytes(
                runtime.maxMemory()
            )).toString() + ", allocated: " + formatBytes(runtime.totalMemory())).toString() + ", available: " + formatBytes(
                heapAvailable
            )
        }, low=$heapLow
[MemoryState] pid: ${memState.pid}, trimlevel: ${trimlvl2str(memState.lastTrimLevel)}""".toString() +
                "\n[MemoryInfo] available: " + formatBytes(memoryInfo.availMem)).toString() + ", total: " + formatBytes(
            memoryInfo.totalMem
        )).toString() + ", lowthresh: " + formatBytes(memoryInfo.threshold)).toString() + ", low=" + memoryInfo.lowMemory +
                "\n[MemoryClass] standard: " + activityManager.memoryClass + " MB, large: " + activityManager.largeMemoryClass + " MB"
    }

    private fun getLocalWifiIpAddress(context: Context): String? {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                ?: return null
        val connInfo = wifiManager.connectionInfo
        if (connInfo != null) {
            var ipAddress = connInfo.getIpAddress()
            if (ipAddress == 0) return null
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                ipAddress = Integer.reverseBytes(ipAddress)
            }
            val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
            val ipAddressString: String = try {
                InetAddress.getByAddress(ipByteArray).hostAddress!!
            } catch (ex: UnknownHostException) {
                return null
            }
            return ipAddressString
        }
        return null
    }

    fun iterPcapRecords(data: ByteArray, pcapngFormat: Boolean): Iterator<Int> {
        val buf = ByteBuffer.wrap(data)
        buf.order(ByteOrder.nativeOrder())

        return if (pcapngFormat) {
            // PCAPNG
            object : Iterator<Int> {
                override fun hasNext(): Boolean {
                    // 12: min block size
                    return buf.remaining() >= 12
                }

                override fun next(): Int {
                    val totalLen = buf.getInt(buf.position() + 4)
                    buf.position(buf.position() + totalLen)
                    return totalLen
                }
            }
        } else {
            // PCAP
            object : Iterator<Int> {
                override fun hasNext(): Boolean {
                    // 16: sizeof(pcap_rec)
                    return buf.remaining() > 16
                }

                override fun next(): Int {
                    val recLen = buf.getInt(buf.position() + 8) + 16
                    buf.position(buf.position() + recLen)
                    return recLen
                }
            }
        }
    }

    fun now(): Long {
        val calendar: Calendar = Calendar.getInstance()
        return calendar.getTimeInMillis() / 1000
    }


    fun getLocalIPAddress(context: Context): String {
        val vpnIp: InetAddress = try {
            InetAddress.getByName(CaptureService.VPN_IP_ADDRESS)
        } catch (e: UnknownHostException) {
            return ""
        }

        // try to get the WiFi IP address first
        val wifiIp = getLocalWifiIpAddress(context)
        if (wifiIp != null && wifiIp != "0.0.0.0") {
            Log.d("getLocalIPAddress", "Using WiFi IP: $wifiIp")
            return wifiIp
        }

        // otherwise search for other network interfaces
        // https://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code
        try {
            val interfaces: List<NetworkInterface> =
                Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isVirtual) {
                    val addrs: List<InetAddress> = Collections.list(intf.getInetAddresses())
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress
                            && addr.isSiteLocalAddress /* Exclude public IPs */
                            && addr != vpnIp
                        ) {
                            val sAddr = addr.hostAddress
                            if (sAddr != null && addr is Inet4Address && sAddr != "0.0.0.0") {
                                Log.d(
                                    "getLocalIPAddress",
                                    "Using interface '" + intf.name + "' IP: " + sAddr
                                )
                                return sAddr
                            }
                        }
                    }
                }
            }
        } catch (ignored: java.lang.Exception) {
        }

        // Fallback
        Log.d("getLocalIPAddress", "Using fallback IP")
        return "127.0.0.1"
    }


    fun getAvailableHeap(): Long {
        val runtime = Runtime.getRuntime()

        // maxMemory: max memory which can be allocated on this app vm (should correspond to getMemoryClass)
        // totalMemory: currently allocated memory (used/unused) by the vm
        // freeMemory: free portion of the totalMemory
        val unallocated = runtime.maxMemory() - runtime.totalMemory()
        return unallocated + runtime.freeMemory()
    }

    fun formatBytes(bytes: Long): String {
        val divisor: Long
        val suffix: String
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) {
            divisor = 1024
            suffix = "KB"
        } else if (bytes < 1024 * 1024 * 1024) {
            divisor = (1024 * 1024).toLong()
            suffix = "MB"
        } else {
            divisor = (1024 * 1024 * 1024).toLong()
            suffix = "GB"
        }
        return String.format("%.1f %s", bytes.toFloat() / divisor, suffix)
    }

    fun trimlvl2str(lvl: Int): String {
        return when (lvl) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
            else -> "TRIM_UNKNOWN"
        }
    }

    fun sendImportantNotification(context: Context?, id: Int, notification: Notification) {
        val man = NotificationManagerCompat.from(context!!)
        if (!man.areNotificationsEnabled()) {
            val title = notification.extras.getString(Notification.EXTRA_TITLE)
            val description = notification.extras.getString(Notification.EXTRA_TEXT)
            val text = "$title - $description"
            Log.w(
                TAG,
                "Important notification not sent because notifications are disabled: $text"
            )

            // Try with toast (will only work if PCAPdroid is in the foreground)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        } else man.notify(id, notification)
    }

    fun hasEncryptedPayload(app: AppDescriptor, conn: ConnectionDescriptor): Boolean {
        return app.getPackageName() == "org.telegram.messenger" || conn.info != null && conn.info == "g.whatsapp.net" && conn.l7proto != "DNS" || app.getUid() == 1000 && conn.dstPort >= 5228 && conn.dstPort <= 5230 || app.getUid() == 1000 && (conn.dstPort === 2195 || conn.dstPort === 2196 || conn.dstPort === 5223)
    }

    fun formatNumber(context: Context?, num: Long): String {
        val locale = getPrimaryLocale(context!!)
        return String.format(locale, "%,d", num)
    }

    // --------------------------------------- HELPER---------------------------------------------//
    fun showToastLong(context: Context, id: Int, vararg args: Any) {
        val msg = context.resources.getString(id, *args)
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    // NOTE: base32 padding not supported
    fun base32Decode(input: String): ByteArray {
        var s = input
        s = s.uppercase(Locale.getDefault()).replace("\n", "")
        val rv = ByteArray(s.length * 5 / 8)
        var i = 0
        var bitsRemaining = 8
        var curByte: Byte = 0
//        for (k in 0 until s.length) {
//            var `val`: Int
//            val c = s[k]
//            `val` =
//                when (c) {
//                    in '2'..'7' -> 26 + (c.code - '2'.code)
//                    in 'A'..'Z' -> c.code - 'A'.code
//                    else -> throw IllegalArgumentException(
//                        "invalid BASE32 string or unsupported padding"
//                    )
//                }
        for (element in s) {
            val `val`: Int = when (element) {
                in '2'..'7' -> 26 + (element.code - '2'.code)
                in 'A'..'Z' -> element.code - 'A'.code
                else -> throw IllegalArgumentException(
                    "invalid BASE32 string or unsupported padding"
                )
            }

            // https://stackoverflow.com/questions/641361/base32-decoding
            if (bitsRemaining > 5) {
                val mask = `val` shl bitsRemaining - 5
                curByte = (curByte.toInt() or mask).toByte()
                bitsRemaining -= 5
            } else {
                val mask = `val` shr 5 - bitsRemaining
                curByte = (curByte.toInt() or mask).toByte()
                rv[i++] = curByte
                curByte = (`val` shl 3 + bitsRemaining).toByte()
                bitsRemaining += 3
            }
        }
        if (i < rv.size) rv[i] = curByte
        return rv
    }

    // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
    fun byteArrayToHex(bytes: ByteArray, size: Int): String {
        val hexChars = CharArray(size * 2)
        for (j in 0 until size) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }

    fun genRandomString(length: Int): String {
        val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val sb = StringBuilder(length)
        val rnd = Random()
        for (i in 0 until length) sb.append(charset[rnd.nextInt(charset.length)])
        return sb.toString()
    }

    // www.example.org -> example.org
    fun cleanDomain(input: String): String {
        var domain = input
        if (domain.startsWith("www.")) domain = domain.substring(4)
        return domain
    }

    @SuppressLint("DefaultLocale")
    fun rootGrantPermission(context: Context, perm: String): Boolean {
        return CaptureService.rootCmd(
            "pm",
            String.format(
                "grant --user %d %s %s",
                getUserId(getPCADroidUid(context)),
                BuildConfig.APPLICATION_ID,
                perm
            )
        ) == 0
    }

    fun downloadFile(_url: String, path: String): Boolean {
        var hasContents = false
        try {
            FileOutputStream("$path.tmp").use { out ->
                BufferedOutputStream(out).use { bos ->
                    val url = URL(_url)
                    val con =
                        url.openConnection() as HttpsURLConnection
                    try {
                        // Necessary otherwise the connection will stay open
                        con.setRequestProperty("Connection", "Close")
                        con.setConnectTimeout(5000)
                        con.setReadTimeout(5000)
                        try {
                            BufferedInputStream(con.inputStream).use { `in` ->
                                val bytesIn = ByteArray(4096)
                                var read: Int
                                while (`in`.read(bytesIn).also { read = it } != -1) {
                                    bos.write(bytesIn, 0, read)
                                    hasContents = hasContents or (read > 0)
                                }
                            }
                        } catch (socketTimeoutException: SocketTimeoutException) {
                            Log.w(TAG, "Timeout while fetching $_url")
                        }
                    } finally {
                        con.disconnect()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (!hasContents) {
            try {
                File("$path.tmp").delete() // if exists
            } catch (ignored: Exception) {
                // ignore
            }
            return false
        }

        // Only write the target path if it was successful
        return File("$path.tmp").renameTo(File(path))
    }


    fun safeClose(obj: Closeable?) {
        if (obj == null) return
        try {
            obj.close()
        } catch (e: IOException) {
            Log.w(TAG, e.localizedMessage!!)
        }
    }
}