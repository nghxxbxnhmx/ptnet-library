package com.ftel.ptnetlibrary.controller

expect class NsLookup {
    fun execute(domainName: String, dnsServer: String): ArrayList<String>
}