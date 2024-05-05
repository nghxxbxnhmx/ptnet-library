package com.ftel.ptnetlibrary.dto

import com.google.gson.annotations.SerializedName

data class DNSResponseDTO(
    @SerializedName("Status")
    val status: Int = 0,
    @SerializedName("TC")
    val tc: Boolean = false,
    @SerializedName("RD")
    val rd: Boolean = false,
    @SerializedName("RA")
    val ra: Boolean = false,
    @SerializedName("AD")
    val ad: Boolean = false,
    @SerializedName("CD")
    val cd: Boolean = false,
    @SerializedName("Question")
    val question: List<QuestionDTO> = emptyList(),
    @SerializedName("Answer")
    val answer: List<AnswerDTO> = emptyList()
)

data class QuestionDTO(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("type")
    val type: Int = 0
)

data class AnswerDTO(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("type")
    val type: Int = 0,
    @SerializedName("TTL")
    val ttl: Int = 0,
    @SerializedName("data")
    val data: String = ""
)