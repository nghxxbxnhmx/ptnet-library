package com.ftel.ptnetlibrary.services

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

actual class PageLoadService {
    private val client = OkHttpClient()

    actual fun pageLoadTimer(address: String): Double {
        if (address.replace(" ", "").isBlank()) {
            return -1.0
        }

        var url: String = ""
        if (address.contains("http://") || address.contains("https://")) {
            url = address.trim()
        } else {
            url = "http://${address.trim()}"
        }

        val request: Request = Request.Builder().url(url).build()

        val startTime = System.currentTimeMillis()
        return try {
            val response: Response = client.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            response.body
                ?.close()
            duration.toDouble()
        } catch (e: IOException) {
            Log.d("PageLoad", "Get time throw error! (check network connected)", e);
            -2.0
        }
    }
}
// PageLoad's error Unable to resolve host "..."
// Check Internet access - not stable

// Response's error CLEARTEXT communication to " ... " not permitted by network security policy
// Check manifest
//  <application
//        android:usesCleartextTraffic="true"
//        ...
//   />