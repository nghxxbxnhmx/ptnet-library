package com.ftel.ptnetlibrary.ndpi.models

import com.maxmind.db.MaxMindDbConstructor
import com.maxmind.db.MaxMindDbParameter
import java.io.Serializable


object Geomodel {

    class CountryResult {
        lateinit var country: Country

        @MaxMindDbConstructor
        constructor(@MaxMindDbParameter(name = "country") country: Country) {
            this.country = country
        }
    }

    class Country : Serializable {
        lateinit var isoCode: String

        // https://db-ip.com/db/format/ip-to-country/mmdb.html
        @MaxMindDbConstructor
        constructor(@MaxMindDbParameter(name = "iso_code") isoCode: String) {
            this.isoCode = isoCode
        }
    }

    class ASN : Serializable {
        var number: Long
        lateinit var asname: String

        constructor() {
            number = 0
            asname = ""
        }

        // https://dev.maxmind.com/geoip/docs/databases/asn?lang=en#blocks-files
        @MaxMindDbConstructor
        constructor(
            @MaxMindDbParameter(name = "autonomous_system_number") number: Long,
            @MaxMindDbParameter(name = "autonomous_system_organization") asname: String?
        ) {
            this.number = number
            this.asname = asname!!
        }

        fun isKnown(): Boolean {
            return number != 0L
        }

        override fun toString(): String {
            return if (number == 0L) "Unknown ASN" else "AS$number - $asname"
        }
    }
}
