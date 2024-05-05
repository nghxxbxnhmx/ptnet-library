package com.ftel.ptnetlibrary.services


expect class NsLookupService {
    fun lookupResponse_api(address: String): String
    fun lookupResponse_dnsjava(domainName: String, dnsServer: String): String
}