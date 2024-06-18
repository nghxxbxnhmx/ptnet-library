package com.ftel.ptnetlibrary.services

import android.util.Log
import org.xbill.DNS.*

class NsLookupService {
    fun execute(domainName: String, dnsServer: String): ArrayList<String> {
        //        DNS FPT HCM
        //        210.245.31.220
        //        210.245.31.221
        //
        //        DNS FPT HNI
        //        210.245.1.253
        //        210.245.1.254
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


    private fun parseLookupResult(lookupResult: ArrayList<String>): ArrayList<String> {
        val result: ArrayList<String> = ArrayList<String>();
        try {
            lookupResult.forEach { record ->
                result.add(parseIpAddress(record.toString()))
            }
        } catch (e: Exception) {
            Log.e("NsLookupService", "func parseLookupResult throw error!", e)
            return result
        }
        return result
    }
}