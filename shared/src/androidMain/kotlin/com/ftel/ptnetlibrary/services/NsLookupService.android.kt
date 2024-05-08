package com.ftel.ptnetlibrary.services

import com.ftel.ptnetlibrary.dto.AnswerDTO
import org.xbill.DNS.*

actual class NsLookupService {
    actual fun execute(domainName: String, dnsServer: String): ArrayList<AnswerDTO> {
        return parseLookupResult(lookupProcess(domainName, dnsServer))
    }

    private fun lookupProcess(dName: String, dServer: String): ArrayList<String> {
        // Perform DNS query
        val resolver: Resolver = SimpleResolver(dServer)
        val lookup = Lookup(dName, Type.A)
        lookup.setResolver(resolver)
        val records: Array<out Record>? = lookup.run()
        val result = ArrayList<String>()

        if (lookup.result == Lookup.SUCCESSFUL) {
            records?.forEach { record ->
                result.add(record.toString())
            }
        }

        return result
    }

    private fun parseDomain(input: String): String {
        val domainMatch = Regex("^\\S+")
        return domainMatch.find(input)?.value ?: ""
    }

    private fun parseIpAddress(input: String): String {
        val ipMatch = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(input)
        return ipMatch?.groups?.get(1)?.value ?: ""
    }

    private fun parseTTL(input: String): Int {
        val ttlMatch = Regex("(\\d+)\\s+IN")
        return ttlMatch.find(input)?.groups?.get(1)?.value?.toIntOrNull()!!
    }

    private fun parseType(input: String): Int {
        val typeToInt = mapOf("A" to 1, "AAAA" to 2, "CNAME" to 3, "MX" to 4, "NS" to 5)
        val typeMatch = Regex("IN\\s+(\\S+)")
        return typeMatch.find(input)?.groups?.get(1)?.value?.let { typeToInt[it] }!!
    }


    private fun parseLookupResult(lookupResult: ArrayList<String>): ArrayList<AnswerDTO> {
        val answerList: ArrayList<AnswerDTO> = ArrayList<AnswerDTO>()
        try {
            lookupResult.forEach { record ->
                val answerDTO = AnswerDTO(
                    name = parseDomain(record.toString()),
                    data = parseIpAddress(record.toString()),
                    ttl = parseTTL(record.toString()),
                    type = parseType(record.toString())
                );
                answerList.add(answerDTO)
            }
        } catch (e: Exception) {
            return answerList
        }
        return answerList
    }
}