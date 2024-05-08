package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.AnswerDTO


expect class NsLookupService {
//    fun lookupResponse_api(address: String): String
    fun execute(domainName: String, dnsServer: String): ArrayList<AnswerDTO>
}