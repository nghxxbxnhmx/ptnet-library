package com.ftel.ptnetlibrary.ndpi.models

import java.io.Serializable

object MitmAPI {
    const val PACKAGE_NAME = "com.ftel.ptnetlibrary.ndpi.models"
    const val MITM_SERVICE = "$PACKAGE_NAME.MitmService"
    const val MSG_ERROR = -1
    const val MSG_START_MITM = 1
    const val MSG_GET_CA_CERTIFICATE = 2
    const val MSG_STOP_MITM = 3
    const val MSG_DISABLE_DOZE = 4
    const val MITM_CONFIG = "mitm_config"
    const val CERTIFICATE_RESULT = "certificate"
    const val SSLKEYLOG_RESULT = "sslkeylog"

    class MitmConfig : Serializable {
        var proxyPort = 0 // the SOCKS5 port to use to accept mitm-ed connections
        var transparentMode =
            false // true to use transparent proxy mode, false to use SOCKS5 proxy mode
        var sslInsecure = false // true to disable upstream certificate check
        var dumpMasterSecrets =
            false // true to enable the TLS master secrets dump messages (similar to SSLKEYLOG)
        var shortPayload = false // if true, only the initial portion of the payload will be sent
        var proxyAuth: String? = null // SOCKS5 proxy authentication, "user:pass"
        var additionalOptions: String? = null // provide additional options to mitmproxy
    }
}