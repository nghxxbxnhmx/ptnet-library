package com.ftel.ptnetlibrary.services


expect class NsLookupService {
    fun lookupResponse(address: String): String
}