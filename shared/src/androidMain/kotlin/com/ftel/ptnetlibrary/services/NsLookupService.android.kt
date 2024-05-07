package com.ftel.ptnetlibrary.services

// Call API Service
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.ftel.ptnetlibrary.dto.DNSResponseDTO
import com.google.gson.Gson
import org.xbill.DNS.*

actual class NsLookupService {
    private var dnsResponseDTOResult = mutableStateOf<DNSResponseDTO?>(null)
    private var callApiService = CallApiService()
    private var result = ""
    actual fun lookupResponse_api(address: String): String {
        // Log.d("Initialize - Result ", result)
        dnsResponseDTOResult = lookupProcess(address)
        dnsResponseDTOResult.value?.let { dnsResponseDTO ->
            dnsResponseDTO.answer.forEach() { item ->
                result += "${item.data}\n"
            }
            // Log.d("Final - Result ", result)
        }
        return result
    }

    private fun lookupProcess(address: String): MutableState<DNSResponseDTO?> {
        val url = "https://dns.google/resolve?name=$address"
        val gson = Gson()
        // Log.d("Process - APIs url", "Url:$url")

        val jsonString = callApiService.getApiResponse(url)
        // Log.d("Process - APis result", jsonString)
        dnsResponseDTOResult.value = gson.fromJson(jsonString, DNSResponseDTO::class.java)
        // Log.d("Process - DTO result", "${dnsResponseDTOResult.value}")
        return dnsResponseDTOResult
    }

    var dName: String = ""
    var dServer: String = ""
    actual fun lookupResponse_dnsjava(domainName: String, dnsServer: String): String {
        dName = domainName;
        dServer = dnsServer
        if (domainName.isEmpty()) {
            Log.d("Lookup DnsJava", "Empty Domain -> zing.vn")
            dName = "zing.vn"
        }

        if (dnsServer.isEmpty()) {
            Log.d("Lookup DnsJava", "Empty DnsServer -> 8.8.8.8")
            dServer = "8.8.8.8" // Default DNS server (Google DNS)
        }

        try {
            dnsJava_process().forEach { record ->
                val ipMatch = Regex("(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(record)
                result += "${ipMatch?.groups?.get(1)?.value ?: ""}\n"
            }
            Log.d("NsLookupService", "DNS Result:\n$result ")
        } catch (e: Exception) {
            Log.d("NsLookupService", "DNS query exception: " + e.message)
            return ""
        }
        return result
    }

    private fun dnsJava_process(): ArrayList<String> {
        // Perform DNS query
        val resolver: Resolver = SimpleResolver(dServer)
//        Log.d("NsLookupService", "DNS Resolver:\n$resolver ")
        val lookup = Lookup(dName, Type.A)
        lookup.setResolver(resolver)
//        Log.d("NsLookupService", "DNS Lookup:\n$lookup ")
        val records: Array<out Record>? = lookup.run()
//        Log.d("NsLookupService", "DNS records:\n$records ")
        val result = ArrayList<String>()

        if (lookup.result == Lookup.SUCCESSFUL) {
            records?.forEach { record ->
                result.add(record.toString())
            }
        } else {
            result.add("DNS query failed - check network connected")
        }
        return result
    }
}

// Log
// Initialize - Result
//  Process - APIs url
//  -> CallApiServices
//  Process - APis result
//  Process - DTO result
// Final - Result

// DNS Result:
// DNS query failed - Check network connected