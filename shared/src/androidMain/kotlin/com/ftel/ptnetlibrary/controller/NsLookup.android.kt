package com.ftel.ptnetlibrary.controller

import com.ftel.ptnetlibrary.services.NsLookupService

actual class NsLookup {
    private val nsLookupService = NsLookupService()
    actual fun execute(domainName: String, dnsServer: String): ArrayList<String> {
        return nsLookupService.execute(domainName, dnsServer)
    }
}