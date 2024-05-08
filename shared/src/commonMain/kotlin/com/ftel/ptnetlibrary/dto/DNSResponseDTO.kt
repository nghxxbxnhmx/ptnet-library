package com.ftel.ptnetlibrary.dto

data class DNSResponseDTO(
    val status: Int = 0,
    val tc: Boolean = false,
    val rd: Boolean = false,
    val ra: Boolean = false,
    val ad: Boolean = false,
    val cd: Boolean = false,
    val question: List<QuestionDTO> = emptyList(),
    val answer: List<AnswerDTO> = emptyList()
)

data class QuestionDTO(
    val name: String = "",
    val type: Int = 0
)

data class AnswerDTO(
    val name: String = "",
    val type: Int = 0,
    val ttl: Int = 0,
    val data: String = ""
)