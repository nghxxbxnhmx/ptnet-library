package com.ftel.ptnetlibrary.ndpi.models

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.Utils.isRootAvailable
import com.ftel.ptnetlibrary.ndpi.models.MitmAddon.Companion.isCAInstallationSkipped

import com.ftel.ptnetlibrary.BuildConfig

class Prefs {
    companion object {
        val DUMP_NONE = "none"
        val DUMP_HTTP_SERVER = "http_server"
        val DUMP_UDP_EXPORTER = "udp_exporter"
        val DUMP_PCAP_FILE = "pcap_file"
        val DEFAULT_DUMP_MODE = DUMP_NONE
        val IP_MODE_IPV4_ONLY = "ipv4"
        val IP_MODE_IPV6_ONLY = "ipv6"
        val IP_MODE_BOTH = "both"
        val IP_MODE_DEFAULT = IP_MODE_IPV4_ONLY
        val BLOCK_QUIC_MODE_NEVER = "never"
        val BLOCK_QUIC_MODE_ALWAYS = "always"
        val BLOCK_QUIC_MODE_TO_DECRYPT = "to_decrypt"
        val BLOCK_QUIC_MODE_DEFAULT = BLOCK_QUIC_MODE_NEVER
        val PAYLOAD_MODE_NONE = "none"
        val PAYLOAD_MODE_MINIMAL = "minimal"
        val PAYLOAD_MODE_FULL = "full"
        val DEFAULT_PAYLOAD_MODE = PAYLOAD_MODE_MINIMAL

        // used to initialize the whitelist with some safe defaults
        val FIREWALL_WHITELIST_INIT_VER = 1
        val PREF_COLLECTOR_IP_KEY = "collector_ip_address"
        val PREF_COLLECTOR_PORT_KEY = "collector_port"
        val PREF_SOCKS5_PROXY_IP_KEY = "socks5_proxy_ip_address"
        val PREF_SOCKS5_PROXY_PORT_KEY = "socks5_proxy_port"
        val PREF_CAPTURE_INTERFACE = "capture_interface"
        val PREF_MALWARE_DETECTION = "malware_detection"
        val PREF_FIREWALL = "firewall"
        val PREF_TLS_DECRYPTION_KEY = "tls_decryption"
        val PREF_APP_FILTER = "app_filter"
        val PREF_HTTP_SERVER_PORT = "http_server_port"
        val PREF_PCAP_DUMP_MODE = "pcap_dump_mode_v2"
        val PREF_IP_MODE = "ip_mode"
        val PREF_APP_LANGUAGE = "app_language"
        val PREF_APP_THEME = "app_theme"
        val PREF_ROOT_CAPTURE = "root_capture"
        val PREF_VISUALIZATION_MASK = "vis_mask"
        val PREF_MALWARE_WHITELIST = "malware_whitelist"
        val PREF_PCAPDROID_TRAILER = "pcapdroid_trailer"
        val PREF_BLOCKLIST = "bl"
        val PREF_FIREWALL_WHITELIST_MODE = "firewall_wl_mode"
        val PREF_FIREWALL_WHITELIST_INIT_VER = "firewall_wl_init"
        val PREF_FIREWALL_WHITELIST = "firewall_whitelist"
        val PREF_DECRYPTION_LIST = "decryption_list"
        val PREF_START_AT_BOOT = "start_at_boot"
        val PREF_SNAPLEN = "snaplen"
        val PREF_MAX_PKTS_PER_FLOW = "max_pkts_per_flow"
        val PREF_MAX_DUMP_SIZE = "max_dump_size"
        val PREF_SOCKS5_ENABLED_KEY = "socks5_enabled"
        val PREF_SOCKS5_AUTH_ENABLED_KEY = "socks5_auth_enabled"
        val PREF_SOCKS5_USERNAME_KEY = "socks5_username"
        val PREF_SOCKS5_PASSWORD_KEY = "socks5_password"
        val PREF_TLS_DECRYPTION_SETUP_DONE = "tls_decryption_setup_ok"
        val PREF_CA_INSTALLATION_SKIPPED = "ca_install_skipped"
        val PREF_FULL_PAYLOAD = "full_payload"
        val PREF_BLOCK_QUIC = "block_quic_mode"
        val PREF_AUTO_BLOCK_PRIVATE_DNS = "auto_block_private_dns"
        val PREF_APP_VERSION = "appver"
        val PREF_LOCKDOWN_VPN_NOTICE_SHOWN = "vpn_lockdown_notice"
        val PREF_VPN_EXCEPTIONS = "vpn_exceptions"
        val PREF_PORT_MAPPING = "port_mapping"
        val PREF_PORT_MAPPING_ENABLED = "port_mapping_enabled"
        val PREF_BLOCK_NEW_APPS = "block_new_apps"
        val PREF_PAYLOAD_NOTICE_ACK = "payload_notice"
        val PREF_REMOTE_COLLECTOR_ACK = "remote_collector_notice"
        val PREF_MITMPROXY_OPTS = "mitmproxy_opts"
        val PREF_DNS_SERVER_V4 = "dns_v4"
        val PREF_DNS_SERVER_V6 = "dns_v6"
        val PREF_USE_SYSTEM_DNS = "system_dns"
        val PREF_PCAPNG_ENABLED = "pcapng_format"
        val PREF_RESTART_ON_DISCONNECT = "restart_on_disconnect"

        fun defaultSetting(context: Context): SharedPreferences {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = sharedPreferences.edit()
            // DNS Settings
            editor.putString(Prefs.PREF_DNS_SERVER_V4, "1.1.1.1")
            editor.putString(Prefs.PREF_DNS_SERVER_V6, "2606:4700:4700::1111")
            editor.putBoolean(Prefs.PREF_USE_SYSTEM_DNS, true)
            // GeoIP Settings - Disabled (Not need export database

            editor.putString(Prefs.PREF_PCAP_DUMP_MODE, DEFAULT_DUMP_MODE)
            // DUMPMODE
            //  addr = InetAddress.getByName(collectorIP)
            //  pcapng_format : true/false
            // mPcapUri <- not null pcap_ui or Download though pcap_name
            // Dumper for each dump_mode
            // - HTTP_SERVER -> HTTPServer(http_server_port, pcapng_format)
            // - PCAP_FILE   -> FileDumper(context, mPcapUri)
            // - UDP_EXPORTER  -> UDPDumper(InetSocketAddress(addr, collector_port), pcapng_format)
            editor.putString(Prefs.PREF_HTTP_SERVER_PORT, "8080")
            editor.putString("pcap_uri", "")
            editor.putString("pcap_name", "")
            editor.putString(Prefs.PREF_COLLECTOR_IP_KEY, "127.0.0.1")
            editor.putString(Prefs.PREF_COLLECTOR_PORT_KEY, "1234")

            // Socks5
            // Check if Socks5 enabled (use proxy or tls decryption)
            editor.putBoolean(Prefs.PREF_SOCKS5_ENABLED_KEY, false)
            // Decrypt the SSL/TLS traffic by performing mitm.
            // This may now work with some apps, check out the user guide
            editor.putBoolean(Prefs.PREF_TLS_DECRYPTION_KEY, false)

            editor.putString(Prefs.PREF_SOCKS5_PROXY_IP_KEY, "4.4.4.4")
            editor.putString(Prefs.PREF_SOCKS5_PROXY_PORT_KEY, "8080")
            // Authenticate to the proxy via username and password
            editor.putBoolean(Prefs.PREF_SOCKS5_AUTH_ENABLED_KEY, false)
            editor.putString(Prefs.PREF_SOCKS5_USERNAME_KEY, "")
            editor.putString(Prefs.PREF_SOCKS5_PASSWORD_KEY, "")

            // Capture Settings
            // Allows PCAPdroid to run with other VPN apps
            editor.putBoolean(Prefs.PREF_ROOT_CAPTURE, false)
            // Capture interface
            editor.putString(Prefs.PREF_CAPTURE_INTERFACE, "@inet")
            // Auto-starts capture after boot
            editor.putBoolean(Prefs.PREF_START_AT_BOOT, false)
            // Automatically restart the capture after being stopped by other VPN apps
            editor.putBoolean(Prefs.PREF_RESTART_ON_DISCONNECT, false)
            // A trailer containing additional metadata (such as the app name) will be added to the dumped packets
            editor.putBoolean(Prefs.PREF_PCAPDROID_TRAILER, false)


            // Filter <- Set<App>
            editor.putStringSet(Prefs.PREF_APP_FILTER, HashSet<String>())


            // SECURITY
            // Detect connections to known malicious hosts via third-party blacklists
            editor.putBoolean(Prefs.PREF_MALWARE_DETECTION, true)

            // OTHERS
            editor.putString(Prefs.PREF_APP_THEME, "system")
            editor.putString(Prefs.PREF_APP_LANGUAGE, "system")
            editor.putString(Prefs.PREF_IP_MODE, Prefs.IP_MODE_IPV4_ONLY)

            // State: Never / Always / Only for connections to decrypt
            editor.putString(Prefs.PREF_BLOCK_QUIC, Prefs.BLOCK_QUIC_MODE_DEFAULT)

            // Detect and possibly block private DNS to inspect DNS traffic.
            // Disabling this can hinder traffic analysis
            editor.putBoolean(Prefs.PREF_AUTO_BLOCK_PRIVATE_DNS, true)


            // Dump packets in the pcapng dump format, which allows embedding TLS decryption secrets
            editor.putBoolean(Prefs.PREF_PCAPNG_ENABLED, true)

            // Show the full connections payload (e.g. the full HTTP request and response).
            // This requires a lot of memory, don't use in the long run
            editor.putBoolean(Prefs.PREF_FULL_PAYLOAD, false)


            editor.putString(Prefs.PREF_SNAPLEN, "0")
            editor.putString(Prefs.PREF_MAX_PKTS_PER_FLOW, "0")
            editor.putString(Prefs.PREF_MAX_DUMP_SIZE, "0")
            editor.putString(Prefs.PREF_MITMPROXY_OPTS, "")
            editor.apply()
            return sharedPreferences
        }

        // GET STARTED -----------------------------------------------------------------------------
        fun getDumpMode(pref: String?): DumpMode {
            return when (pref) {
                DUMP_HTTP_SERVER -> DumpMode.HTTP_SERVER
                DUMP_PCAP_FILE -> DumpMode.PCAP_FILE
                DUMP_UDP_EXPORTER -> DumpMode.UDP_EXPORTER
                else -> DumpMode.NONE
            }
        }

        fun getIPMode(pref: String?): IpMode {
            return when (pref) {
                IP_MODE_IPV6_ONLY -> IpMode.IPV6_ONLY
                IP_MODE_BOTH -> IpMode.BOTH
                else -> IpMode.IPV4_ONLY
            }
        }

        fun getBlockQuicMode(pref: String?): BlockQuicMode {
            return when (pref) {
                BLOCK_QUIC_MODE_ALWAYS -> BlockQuicMode.ALWAYS
                BLOCK_QUIC_MODE_TO_DECRYPT -> BlockQuicMode.TO_DECRYPT
                else -> BlockQuicMode.NEVER
            }
        }

        fun getPayloadMode(pref: String?): PayloadMode {
            return when (pref) {
                PAYLOAD_MODE_MINIMAL -> PayloadMode.MINIMAL
                PAYLOAD_MODE_FULL -> PayloadMode.FULL
                else -> PayloadMode.NONE
            }
        }

        fun getAppVersion(p: SharedPreferences): Int {
            return p.getInt(PREF_APP_VERSION, 0)
        }

        fun refreshAppVersion(p: SharedPreferences) {
            p.edit().putInt(PREF_APP_VERSION, BuildConfig.VERSION_CODE).apply()
        }

        fun setLockdownVpnNoticeShown(p: SharedPreferences) {
            p.edit().putBoolean(PREF_LOCKDOWN_VPN_NOTICE_SHOWN, true).apply()
        }

        fun setFirewallWhitelistInitialized(p: SharedPreferences) {
            p.edit().putInt(PREF_FIREWALL_WHITELIST_INIT_VER, FIREWALL_WHITELIST_INIT_VER).apply()
        }

        fun setPortMappingEnabled(p: SharedPreferences, enabled: Boolean) {
            p.edit().putBoolean(PREF_PORT_MAPPING_ENABLED, enabled).apply()
        }

        /* Prefs with defaults */
        fun getCollectorIp(p: SharedPreferences): String? {
            return p.getString(PREF_COLLECTOR_IP_KEY, "127.0.0.1")
        }

        fun getCollectorPort(p: SharedPreferences): Int {
            return p.getString(PREF_COLLECTOR_PORT_KEY, "1234")!!.toInt()
        }

        fun getDumpMode(p: SharedPreferences): DumpMode {
            return getDumpMode(p.getString(PREF_PCAP_DUMP_MODE, DEFAULT_DUMP_MODE))
        }

        fun getHttpServerPort(p: SharedPreferences): Int {
            return p.getString(PREF_HTTP_SERVER_PORT, "8080")!!.toInt()
        }

        fun getTlsDecryptionEnabled(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_TLS_DECRYPTION_KEY, false)
        }

        fun getSocks5Enabled(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_SOCKS5_ENABLED_KEY, false)
        }

        fun getSocks5ProxyHost(p: SharedPreferences): String? {
            return p.getString(PREF_SOCKS5_PROXY_IP_KEY, "0.0.0.0")
        }

        fun getSocks5ProxyPort(p: SharedPreferences): Int {
            return p.getString(PREF_SOCKS5_PROXY_PORT_KEY, "8080")!!.toInt()
        }

        fun isSocks5AuthEnabled(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_SOCKS5_AUTH_ENABLED_KEY, false)
        }

        fun getSocks5Username(p: SharedPreferences): String? {
            return p.getString(PREF_SOCKS5_USERNAME_KEY, "")
        }

        fun getSocks5Password(p: SharedPreferences): String? {
            return p.getString(PREF_SOCKS5_PASSWORD_KEY, "")
        }

        fun getAppFilter(p: SharedPreferences): Set<String>? {
            return getStringSet(p, PREF_APP_FILTER)
        }

        fun getIPMode(p: SharedPreferences): IpMode {
            return getIPMode(p.getString(PREF_IP_MODE, IP_MODE_DEFAULT))
        }

        fun getBlockQuicMode(p: SharedPreferences): BlockQuicMode {
            return getBlockQuicMode(p.getString(PREF_BLOCK_QUIC, BLOCK_QUIC_MODE_DEFAULT))
        }

        fun useEnglishLanguage(p: SharedPreferences): Boolean {
            return "english" == p.getString(PREF_APP_LANGUAGE, "system")
        }

        fun isRootCaptureEnabled(p: SharedPreferences): Boolean {
            return isRootAvailable() && p.getBoolean(PREF_ROOT_CAPTURE, false)
        }

        fun isPcapdroidTrailerEnabled(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_PCAPDROID_TRAILER, false)
        }

        fun getCaptureInterface(p: SharedPreferences): String? {
            return p.getString(PREF_CAPTURE_INTERFACE, "@inet")
        }

        fun isMalwareDetectionEnabled(ctx: Context, p: SharedPreferences): Boolean {
            return (Billing.newInstance(ctx).isPurchased(Billing.MALWARE_DETECTION_SKU)
                    && p.getBoolean(PREF_MALWARE_DETECTION, true))
        }

        fun isFirewallEnabled(ctx: Context, p: SharedPreferences): Boolean {
            // NOTE: firewall can be disabled at runtime
            return (Billing.newInstance(ctx).isFirewallVisible()
                    && p.getBoolean(PREF_FIREWALL, true))
        }

        fun isPcapngEnabled(ctx: Context, p: SharedPreferences): Boolean {
            return (Billing.newInstance(ctx).isPurchased(Billing.PCAPNG_SKU)
                    && p.getBoolean(PREF_PCAPNG_ENABLED, true))
        }

        fun startAtBoot(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_START_AT_BOOT, false)
        }

        fun restartOnDisconnect(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_RESTART_ON_DISCONNECT, false)
        }

        fun isTLSDecryptionSetupDone(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_TLS_DECRYPTION_SETUP_DONE, false)
        }

        fun getFullPayloadMode(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_FULL_PAYLOAD, false)
        }

        fun isPrivateDnsBlockingEnabled(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_AUTO_BLOCK_PRIVATE_DNS, true)
        }

        fun lockdownVpnNoticeShown(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_LOCKDOWN_VPN_NOTICE_SHOWN, false)
        }

        fun blockNewApps(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_BLOCK_NEW_APPS, false)
        }

        fun isFirewallWhitelistMode(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_FIREWALL_WHITELIST_MODE, false)
        }

        fun isFirewallWhitelistInitialized(p: SharedPreferences): Boolean {
            return p.getInt(PREF_FIREWALL_WHITELIST_INIT_VER, 0) == FIREWALL_WHITELIST_INIT_VER
        }

        fun getMitmproxyOpts(p: SharedPreferences): String? {
            return p.getString(PREF_MITMPROXY_OPTS, "")
        }

        fun isPortMappingEnabled(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_PORT_MAPPING_ENABLED, true)
        }

        fun useSystemDns(p: SharedPreferences): Boolean {
            return p.getBoolean(PREF_USE_SYSTEM_DNS, true)
        }

        fun getDnsServerV4(p: SharedPreferences): String? {
            return p.getString(PREF_DNS_SERVER_V4, "1.1.1.1")
        }

        fun getDnsServerV6(p: SharedPreferences): String? {
            return p.getString(PREF_DNS_SERVER_V6, "2606:4700:4700::1111")
        }

        // Gets a StringSet from the prefs
        // The preference should either be a StringSet or a String
        // An empty set is returned as the default value
        @SuppressLint("MutatingSharedPrefs")
        fun getStringSet(p: SharedPreferences, key: String?): Set<String>? {
            var rv: MutableSet<String>? = null
            try {
                rv = p.getStringSet(key, null)
            } catch (e: ClassCastException) {
                // retry with string
                val s = p.getString(key, "")
                if (s!!.isNotEmpty()) {
                    rv = HashSet()
                    rv.add(s)
                }
            }
            if (rv == null) rv = HashSet()
            return rv
        }

        fun asString(ctx: Context): String {
            val p = PreferenceManager.getDefaultSharedPreferences(ctx)

            // NOTE: possibly sensitive info like the collector IP address not shown
            return """
             DumpMode: ${getDumpMode(p)}
             FullPayload: ${getFullPayloadMode(p)}
             TLSDecryption: ${getTlsDecryptionEnabled(p)}
             TLSSetupOk: ${isTLSDecryptionSetupDone(p)}
             CAInstallSkipped: ${isCAInstallationSkipped(ctx)}
             BlockQuic: ${getBlockQuicMode(p)}
             RootCapture: ${isRootCaptureEnabled(p)}
             Socks5: ${getSocks5Enabled(p)}
             BlockPrivateDns: ${isPrivateDnsBlockingEnabled(p)}
             CaptureInterface: ${getCaptureInterface(p)}
             MalwareDetection: ${isMalwareDetectionEnabled(ctx, p)}
             Firewall: ${isFirewallEnabled(ctx, p)}
             PCAPNG: ${isPcapngEnabled(ctx, p)}
             BlockNewApps: ${blockNewApps(p)}
             TargetApps: ${getAppFilter(p)}
             IpMode: ${getIPMode(p)}
             Trailer: ${isPcapdroidTrailerEnabled(p)}
             StartAtBoot: ${startAtBoot(p)}
             """.trimIndent()
        }
    }

    enum class DumpMode {
        NONE,
        HTTP_SERVER,
        PCAP_FILE,
        UDP_EXPORTER
    }

    enum class IpMode {
        IPV4_ONLY,
        IPV6_ONLY,
        BOTH
    }

    enum class BlockQuicMode {
        NEVER,
        ALWAYS,
        TO_DECRYPT
    }

    enum class PayloadMode {
        NONE,
        MINIMAL,
        FULL
    }
}

