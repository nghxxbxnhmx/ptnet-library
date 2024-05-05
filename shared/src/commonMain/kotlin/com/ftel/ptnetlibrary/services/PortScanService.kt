package com.ftel.ptnetlibrary.services

expect class PortScanService {
    fun portScan(address: String, port: Int): String
}