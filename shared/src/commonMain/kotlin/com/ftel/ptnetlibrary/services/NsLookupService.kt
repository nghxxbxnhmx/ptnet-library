package com.ftel.ptnetlibrary.services

expect class NsLookupService {
//    fun lookupResponse_api(address: String): String
    fun execute(domainName: String, dnsServer: String): ArrayList<String>
}