package com.ftel.ptnetlibrary.services

expect class PageLoadService {
    fun pageLoadTimer(address: String): Double
}