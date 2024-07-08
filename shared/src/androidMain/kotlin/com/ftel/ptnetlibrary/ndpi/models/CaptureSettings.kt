package com.ftel.ptnetlibrary.ndpi.models

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.io.Serializable


class CaptureSettings : Serializable {
    var dump_mode: Prefs.DumpMode
    var app_filter: Set<String>?
    var collector_address: String
    var collector_port: Int
    var http_server_port: Int
    var socks5_enabled: Boolean
    var tls_decryption: Boolean
    var socks5_proxy_address: String
    var socks5_proxy_port: Int
    var socks5_username: String
    var socks5_password: String
    var ip_mode: Prefs.IpMode
    var input_pcap_path: String? = null
    var root_capture: Boolean
    var pcapdroid_trailer: Boolean
    var full_payload: Boolean
    var block_quic_mode: Prefs.BlockQuicMode
    var auto_block_private_dns: Boolean
    var pcapng_format: Boolean
    var capture_interface: String
    var pcap_uri = ""
    var pcap_name = ""
    var snaplen = 0
    var max_pkts_per_flow = 0
    var max_dump_size = 0
    var mitmproxy_opts: String

    constructor(ctx: Context, prefs: SharedPreferences) {
        dump_mode = Prefs.getDumpMode(prefs)
        app_filter = Prefs.getAppFilter(prefs)
        collector_address = Prefs.getCollectorIp(prefs).toString()
        collector_port = Prefs.getCollectorPort(prefs)
        http_server_port = Prefs.getHttpServerPort(prefs)
        socks5_enabled = Prefs.getSocks5Enabled(prefs)
        socks5_proxy_address = Prefs.getSocks5ProxyHost(prefs).toString()
        socks5_proxy_port = Prefs.getSocks5ProxyPort(prefs)
        socks5_username =
            if (Prefs.isSocks5AuthEnabled(prefs)) Prefs.getSocks5Username(prefs)
                .toString() else ""
        socks5_password =
            if (Prefs.isSocks5AuthEnabled(prefs)) Prefs.getSocks5Password(prefs)
                .toString() else ""
        ip_mode = Prefs.getIPMode(prefs)
        root_capture = Prefs.isRootCaptureEnabled(prefs)
        pcapdroid_trailer = Prefs.isPcapdroidTrailerEnabled(prefs)
        capture_interface = Prefs.getCaptureInterface(prefs).toString()
        tls_decryption = Prefs.getTlsDecryptionEnabled(prefs)
        full_payload = Prefs.getFullPayloadMode(prefs)
        block_quic_mode = Prefs.getBlockQuicMode(prefs)
        auto_block_private_dns = Prefs.isPrivateDnsBlockingEnabled(prefs)
        mitmproxy_opts = Prefs.getMitmproxyOpts(prefs).toString()
        pcapng_format = Prefs.isPcapngEnabled(ctx, prefs)
    }

    constructor(ctx: Context, intent: Intent) {
        dump_mode = Prefs.getDumpMode(getString(intent, "pcap_dump_mode", "none"))
        app_filter = HashSet<String>(getStringList(intent, Prefs.PREF_APP_FILTER))
        collector_address = getString(intent, Prefs.PREF_COLLECTOR_IP_KEY, "127.0.0.1")
        collector_port = getInt(intent, Prefs.PREF_COLLECTOR_PORT_KEY, 1234)
        http_server_port = getInt(intent, Prefs.PREF_HTTP_SERVER_PORT, 8080)
        socks5_enabled = getBool(intent, Prefs.PREF_SOCKS5_ENABLED_KEY, false)
        socks5_proxy_address = getString(intent, Prefs.PREF_SOCKS5_PROXY_IP_KEY, "0.0.0.0")
        socks5_proxy_port = getInt(intent, Prefs.PREF_SOCKS5_PROXY_PORT_KEY, 8080)
        socks5_username = getString(intent, Prefs.PREF_SOCKS5_USERNAME_KEY, "")
        socks5_password = getString(intent, Prefs.PREF_SOCKS5_PASSWORD_KEY, "")
        ip_mode =
            Prefs.getIPMode(getString(intent, Prefs.PREF_IP_MODE, Prefs.IP_MODE_DEFAULT))
        root_capture = getBool(intent, Prefs.PREF_ROOT_CAPTURE, false)
        pcapdroid_trailer = getBool(intent, Prefs.PREF_PCAPDROID_TRAILER, false)
        capture_interface = getString(intent, Prefs.PREF_CAPTURE_INTERFACE, "@inet")
        pcap_uri = getString(intent, "pcap_uri", "")
        pcap_name = getString(intent, "pcap_name", "")
        snaplen = getInt(intent, Prefs.PREF_SNAPLEN, 0)
        max_pkts_per_flow = getInt(intent, Prefs.PREF_MAX_PKTS_PER_FLOW, 0)
        max_dump_size = getInt(intent, Prefs.PREF_MAX_DUMP_SIZE, 0)
        tls_decryption = getBool(intent, Prefs.PREF_TLS_DECRYPTION_KEY, false)
        full_payload = false
        block_quic_mode =
            Prefs.getBlockQuicMode(
                getString(
                    intent,
                    "block_quic",
                    Prefs.BLOCK_QUIC_MODE_DEFAULT
                )
            )
        auto_block_private_dns = getBool(intent, Prefs.PREF_AUTO_BLOCK_PRIVATE_DNS, true)
        mitmproxy_opts = getString(intent, Prefs.PREF_MITMPROXY_OPTS, "")
        pcapng_format =
            getBool(intent, Prefs.PREF_PCAPNG_ENABLED, false) && Billing.newInstance(ctx)
                .isPurchased(Billing.PCAPNG_SKU)
    }

    fun readFromPcap(): Boolean {
        return input_pcap_path != null
    }

    companion object {
        private fun getString(intent: Intent, key: String, defValue: String): String {
            val `val` = intent.getStringExtra(key)
            return `val` ?: defValue
        }

        // get a integer value from the bundle. The value may be represented as an int or as a string.
        private fun getInt(intent: Intent, key: String, defValue: Int): Int {
            val bundle = intent.extras
            val s = bundle!!.getString(key)
            return s?.toInt() ?: bundle.getInt(key, defValue)
        }

        // get a boolean value from the bundle. The value may be represented as a bool or as a string.
        private fun getBool(intent: Intent, key: String, defValue: Boolean): Boolean {
            val bundle = intent.extras
            val s = bundle!!.getString(key)
            return s?.toBoolean() ?: bundle.getBoolean(key, defValue)
        }

        // get a list of comma-separated strings from the bundle
        private fun getStringList(intent: Intent, key: String): List<String> {
            val rv: MutableList<String>
            val s = intent.getStringExtra(key)
            if (s != null) {
                if (s.indexOf(',') < 0) {
                    rv = ArrayList()
                    rv.add(s)
                } else {
                    val arr = s.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    rv = listOf(*arr).toMutableList()
                }
            } else rv = ArrayList()
            return rv
        }
    }
}

