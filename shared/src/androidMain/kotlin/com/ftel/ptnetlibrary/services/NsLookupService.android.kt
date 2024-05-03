package com.ftel.ptnetlibrary.services

// Call API Service
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.ftel.ptnetlibrary.dto.DNSResponseDTO
import com.google.gson.Gson

actual class NsLookupService {
    private var dnsResponseDTOResult = mutableStateOf<DNSResponseDTO?>(null)
    private var callApiService = CallApiService()
    private var result = ""
    actual fun lookupResponse(address: String): String {
        Log.d("Initialize - Result ", result)
        dnsResponseDTOResult = lookupProcess(address)
        dnsResponseDTOResult.value?.let { dnsResponseDTO ->
            dnsResponseDTO.answer.forEach() { item ->
                result += "${item.data}\n"
            }
            Log.d("Final - Result ", result)
        }
        return result
    }

    private fun lookupProcess(address: String): MutableState<DNSResponseDTO?> {
        val url = "https://dns.google/resolve?name=$address"
        val gson = Gson()
        Log.d("Process - APIs url", "Url:$url")

        val jsonString = callApiService.getApiResponse(url)
        Log.d("Process - APis result", jsonString)
        dnsResponseDTOResult.value = gson.fromJson(jsonString, DNSResponseDTO::class.java)
        Log.d("Process - DTO result", "${dnsResponseDTOResult.value}")
        return dnsResponseDTOResult
    }
}

// Log
// Initialize - Result
//  Process - APIs url
//  -> CallApiServices
//  Process - APis result
//  Process - DTO result
// Final - Result