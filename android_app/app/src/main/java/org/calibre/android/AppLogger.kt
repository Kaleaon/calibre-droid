package org.calibre.android

import android.util.Log

object AppLogger {
    private const val BASE_TAG = "CalibreDroid"

    fun d(component: String, message: String) {
        Log.d("$BASE_TAG:$component", message)
    }

    fun i(component: String, message: String) {
        Log.i("$BASE_TAG:$component", message)
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w("$BASE_TAG:$component", message, throwable)
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e("$BASE_TAG:$component", message, throwable)
    }
}
