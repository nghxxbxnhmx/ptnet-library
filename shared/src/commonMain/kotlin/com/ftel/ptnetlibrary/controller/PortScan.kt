package com.ftel.ptnetlibrary.controller

expect class PortScan {
    fun portScan(address: String, port: Int, timeOut: Int): String
}