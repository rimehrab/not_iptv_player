package dev.rimehrab.terebi

import android.app.Application
import android.util.Log

class TerebiApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = Log.getStackTraceString(throwable)
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, trace)
                    .apply()
            } catch (e: Exception) {
                // best effort — don't let the crash handler itself crash harder
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val PREFS_NAME = "iptv_player_prefs"
        const val KEY_LAST_CRASH = "last_crash"
    }
}
