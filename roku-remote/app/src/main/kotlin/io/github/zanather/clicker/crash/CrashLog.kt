package io.github.zanather.clicker.crash

import android.content.Context
import android.os.Build
import io.github.zanather.clicker.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on disk so the next launch can show it. There's no crash-reporting service: this app is
 * sideloaded, and a stack trace the user can copy is the only way a crash on their phone reaches a fix.
 */
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"

    /** Records any uncaught exception, then lets the platform crash the process as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(app, error)
            } catch (_: Throwable) {
                // Never let reporting hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, error: Throwable) {
        file(context).writeText(report(error))
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    private fun report(error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("Clicker ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(time)
            appendLine()
            append(trace)
        }
    }
}
