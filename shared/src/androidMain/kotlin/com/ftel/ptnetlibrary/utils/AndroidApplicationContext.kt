package com.ftel.ptnetlibrary.utils

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("PrivateApi")
fun getAppContext(): Context {
    val contextField = Class.forName("android.app.ActivityThread")
        .getDeclaredField("sCurrentActivityThread")
    contextField.isAccessible = true
    val activityThread = contextField.get(null)
    val appField = activityThread.javaClass.getDeclaredField("mInitialApplication")
    appField.isAccessible = true
    val app = appField.get(activityThread)
    return app as Context
}