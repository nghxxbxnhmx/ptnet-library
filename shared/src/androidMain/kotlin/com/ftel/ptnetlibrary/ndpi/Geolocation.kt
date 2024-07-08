package com.ftel.ptnetlibrary.ndpi

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.ftel.ptnetlibrary.ndpi.Utils.downloadFile
import com.ftel.ptnetlibrary.ndpi.models.Geomodel.ASN
import com.ftel.ptnetlibrary.ndpi.models.Geomodel.CountryResult
import com.ftel.ptnetlibrary.ndpi.Utils
import com.maxmind.db.Reader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date


class Geolocation {
    private val TAG = "Geolocation"
    private lateinit var mContext: Context
    private var mCountryReader: Reader? = null
    private var mAsnReader: Reader? = null

    constructor(ctx: Context) {
        mContext = ctx;
        openDb();
    }

    private fun openDb() {
        try {
            mCountryReader = Reader(getCountryFile(mContext))
            Log.d(TAG, "Country DB loaded: " + mCountryReader!!.metadata)
            mAsnReader = Reader(getAsnFile(mContext))
            Log.d(TAG, "ASN DB loaded: " + mAsnReader!!.metadata)
        } catch (e: IOException) {
            Log.i(TAG, "Geolocation is not available")
        }
    }

    private fun getCountryFile(ctx: Context): File {
        return File(ctx.filesDir.toString() + "/dbip_country_lite.mmdb")
    }

    private fun getAsnFile(ctx: Context): File {
        return File(ctx.filesDir.toString() + "/dbip_asn_lite.mmdb")
    }

    @Throws(IOException::class)
    fun getDbDate(file: File?): Date {
        Reader(file).use { reader -> return reader.metadata.buildDate }
    }

    fun getDbDate(ctx: Context): Date? {
        return try {
            getDbDate(getCountryFile(ctx))
        } catch (ignored: IOException) {
            null
        }
    }

    fun getDbSize(ctx: Context): Long {
        return getCountryFile(ctx).length() + getAsnFile(ctx).length()
    }

    fun deleteDb(ctx: Context) {
        getCountryFile(ctx).delete()
        getAsnFile(ctx).delete()
    }

    @SuppressLint("SimpleDateFormat")
    fun downloadDb(ctx: Context): Boolean {
        val dateid: String = SimpleDateFormat("yyyy-MM").format(Date())
        val countryUrl =
            "https://download.db-ip.com/free/dbip-country-lite-$dateid.mmdb.gz"
        val asnUrl = "https://download.db-ip.com/free/dbip-asn-lite-$dateid.mmdb.gz"
        return try {
            downloadAndUnzip(ctx, "country", countryUrl, getCountryFile(ctx)) &&
                    downloadAndUnzip(ctx, "asn", asnUrl, getAsnFile(ctx))
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    @Throws(IOException::class)
    private fun downloadAndUnzip(ctx: Context, label: String, url: String, dst: File): Boolean {
        val tmpFile = File(ctx.cacheDir.toString() + "/geoip_db.zip")
        val rv = downloadFile(url, tmpFile.absolutePath)
        if (!rv) {
            Log.w(TAG, "Could not download $label db from $url")
            return false
        }
        try {
            FileInputStream(tmpFile.absolutePath).use { `is` ->
                if (!Utils.ungzip(`is`, dst.absolutePath)) {
                    Log.w(TAG, "ungzip of $tmpFile failed")
                    return false
                }

                // Verify - throws IOException on error
                getDbDate(dst)
                return true
            }
        } finally {
            tmpFile.delete()
        }
    }

    fun getCountryCode(addr: InetAddress?): String {
        if (mCountryReader != null) {
            try {
                val res = mCountryReader!!.get(addr, CountryResult::class.java)
                if (res?.country != null) return res.country.isoCode
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // fallback
        return ""
    }

    fun getASN(addr: InetAddress?): ASN {
        if (mAsnReader != null) {
            try {
                val res = mAsnReader!!.get(addr, ASN::class.java)
                if (res != null) return res
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // fallback
        return ASN()
    }
}