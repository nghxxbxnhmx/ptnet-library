package com.ftel.ptnetlibrary.ndpi.models


import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.ftel.ptnetlibrary.ndpi.Utils
import com.ftel.ptnetlibrary.ndpi.services.CaptureService
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays

import com.ftel.ptnetlibrary.R

@SuppressLint("MissingPermission","HardwareIds")
class Billing {
    private val TAG = "Billing"
    private val KEY = "ME4wEAYHKoZIzj0CAQYFK4EEACEDOgAE6cS1N1P0kaiuxq0g70OVVE0uIOD+t809" +
            "Etg3k2h11k8uNvfkx3mL1HTjQyzSfdueyY4DqTW7+sk="
    private val PEER_SKU_KEY = "peer_skus"

    // Resources used in the play build, referenced here to avoid being marked as unused resources
    private val resPlaceholder = intArrayOf(
        R.string.billing_connecting,
        R.string.pending_transaction,
        R.string.feature_not_available,
        R.string.show_me,
        R.string.loading,
        R.string.purchased,
        R.string.no_items_for_purchase,
        R.string.billing_failure,
        R.string.learn_more,
        R.string.buy_action,
        R.string.can_use_purchased_feature,
        R.drawable.ic_shopping_cart,
        R.string.firewall_summary,
        R.string.no_root_firewall,
        R.string.unlock_token,
        R.string.unlock_token_summary,
        R.string.unlock_token_error,
        R.string.license_service_unavailable,
        R.string.requesting_unlock_token,
        R.string.show_action,
        R.string.unlock_token_msg1,
        R.string.qr_license_confirm,
        R.string.qr_purchase_required,
        R.string.license_limit_reached,
        R.string.license_error,
        R.string.requesting_license
    )

    var mContext: Context? = null
    var mPrefs: SharedPreferences? = null

    // this is initialized in MainActivity
    var mPeerSkus = HashSet<String>()

    constructor()

    constructor(ctx: Context) {
        mContext = ctx
        mPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)

        // Load peer skus
        val peerSkus = mPrefs!!.getStringSet(PEER_SKU_KEY, null)
        if (peerSkus != null) {
            mPeerSkus.addAll(peerSkus)
        }
    }

    companion object {
        // SKUs
        val SUPPORTER_SKU = "pcapdroid_supporter"
        val UNLOCK_TOKEN_SKU = "unlock_code"
        val MALWARE_DETECTION_SKU = "malware_detection"
        val FIREWALL_SKU = "no_root_firewall"
        val PCAPNG_SKU = "pcapng"
        val ALL_SKUS = Arrays.asList(
            SUPPORTER_SKU, UNLOCK_TOKEN_SKU, MALWARE_DETECTION_SKU, FIREWALL_SKU, PCAPNG_SKU
        )

        fun newInstance(ctx: Context): Billing {
            return Billing(ctx)
        }
    }


    fun isAvailable(sku: String): Boolean {
        return isPurchased(sku)
    }

    fun isPurchased(sku: String): Boolean {
        return mPeerSkus.contains(sku) || getLicense().isNotEmpty()
    }

    fun isPlayStore(): Boolean {
        return false
    }

    private fun getLicense(): String {
        return mPrefs!!.getString("license", "") ?: ""
    }

    fun connectBilling() {}
    fun disconnectBilling() {}

    @RequiresApi(Build.VERSION_CODES.O)
    fun setLicense(license: String): Boolean {
        var valid = true
        var licenseToSet = license
        if (!isValidLicense(license)) {
            licenseToSet = ""
            valid = false
        }

        mPrefs!!.edit()
            .putString("license", licenseToSet)
            .apply()

        return valid
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun isValidLicense(license: String): Boolean {
        if (license.isEmpty()) return false

        return try {
            val data = Utils.base32Decode(license)
            if (data.size != 60 || data[0] != 'v'.code.toByte() || data[1] != '1'.code.toByte()) return false

            val keyFactory = KeyFactory.getInstance("EC")
            val pk = keyFactory.generatePublic(
                X509EncodedKeySpec(
                    Base64.decode(
                        KEY,
                        Base64.DEFAULT
                    )
                )
            )
            val sig = Signature.getInstance("SHA1withECDSA")
            sig.initVerify(pk)

            val msg = "$SUPPORTER_SKU@${getInstallationId()}"
            sig.update(msg.toByteArray(StandardCharsets.US_ASCII))
            sig.verify(getASN1(data, 4))
        } catch (e: Exception) {
            Log.d(TAG, e.message ?: "Error")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getInstallationId(): String {
        val installationId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Settings.Secure.getString(mContext!!.contentResolver, Settings.Secure.ANDROID_ID)
        } else {
            //<uses-permission android:name="android.permission.READ_PHONE_STATE" />
            Build.getSerial()
        }

        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val digest = md5.digest(installationId.toByteArray())
            "M${Utils.byteArrayToHex(digest, 8)}"
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            "D$installationId"
        }
    }

    private fun getASN1(signature: ByteArray, offset: Int): ByteArray {
        val rLen = 28

        require((signature.size - offset) == 2 * rLen) { "invalid signature length" }

        val rExtra = if (signature[offset] < 0) 1 else 0
        val nExtra = if (signature[offset + rLen] < 0) 1 else 0
        val totLen = 2 * rLen + 6 + rExtra + nExtra
        val rv = ByteArray(totLen)
        var i = 0

        rv[i++] = 0x30
        rv[i++] = (totLen - 2).toByte()

        rv[i++] = 0x02
        rv[i++] = (rLen + rExtra).toByte()
        if (rExtra > 0) rv[i++] = 0x00
        System.arraycopy(signature, offset, rv, i, rLen)
        i += rLen

        rv[i++] = 0x02
        rv[i++] = (rLen + nExtra).toByte()
        if (nExtra > 0) rv[i++] = 0x00
        System.arraycopy(signature, offset + rLen, rv, i, rLen)

        return rv
    }

    fun isFirewallVisible(): Boolean {
        if (!isPurchased(FIREWALL_SKU)) return false

        return if (CaptureService.isServiceActive()) {
            !CaptureService().isCapturingAsRoot() && !CaptureService().isReadingFromPcapFile()
        } else {
            !Prefs.isRootCaptureEnabled(mPrefs!!)
        }
    }

    private fun handlePeerSkus(skus: Set<String>) {
        if (skus == mPeerSkus) return // nothing changed

        mPeerSkus.clear()
        mPeerSkus.addAll(skus)

        Log.i(TAG, "Peer skus updated: $skus")

        mPrefs!!.edit()
            .putStringSet(PEER_SKU_KEY, mPeerSkus)
            .apply()
    }

    fun clearPeerSkus() {
        val emptyHashSet: HashSet<String> = hashSetOf()
        handlePeerSkus(emptyHashSet)
    }
}