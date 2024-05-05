package com.ftel.ptnetlibrary.services

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CallApiService {
    private val client: OkHttpClient = OkHttpClient()

    fun getApiResponse(url: String?): String {
        val request = Request.Builder()
            .url(url.toString())
            .build()
        var result = ""
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                } else {
                    result = response.body!!.string()
                    response.body?.close()
                    result
                }
            }
        } catch (e: IOException) {
            result = "Response's error ${e.message}"
            result
        }
    }

//    @Throws(IOException::class)
//    fun postApiResponse(url: String, jsonBody: String): String {
//        val body: RequestBody =
//            jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
//        val request = Request.Builder()
//            .url(url)
//            .post(body)
//            .build()
//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) throw IOException("Unexpected code $response")
//            return response.body.toString()
//        }
//    }
//
//
//    fun postApiResponse(headers: Headers, url: String, jsonBody: String): String {
//        val body: RequestBody =
//            jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
//        val request = Request.Builder()
//            .url(url)
//            .headers(headers)
//            .post(body)
//            .build()
//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) throw IOException("Unexpected code $response")
//            return response.body.toString()
//        }
//    }
}
