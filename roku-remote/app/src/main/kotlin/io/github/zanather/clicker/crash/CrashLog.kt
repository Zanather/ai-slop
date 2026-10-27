package io.github.zanather.clicker.crash

import android.content.Context
import android.os.Build
import android.os.Process
import io.github.zanather.clicker.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash, plus a short trail of lifecycle breadcrumbs, on disk so they can be shown and copied.
 * There's no crash-reporting service: this app is sideloaded, and text the user can copy is the only way a
 * problem on their phone reaches a fix.
 */
object CrashLog {
    private const val CRASH_FILE = "last-crash.txt"
    private const val TRAIL_FILE = "breadcrumbs.txt"
    private const val TRAIL_KEEP = 200

    @Volatile
    private var dir: File? = null

    /**
     * Records any uncaught exception, then lets the platform crash the process as usual. Call from
     * `Application.attachBaseContext`, which runs before content providers, so startup crashes are caught too.
     */
    fun install(context: Context) {
        dir = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                record(error, thread.name)
            } catch (_: Throwable) {
                // Never let reporting hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
        breadcrumb("process ${Process.myPid()} started: ${device()}")
    }

    /** Appends one line to the trail. Cheap enough for lifecycle callbacks; never throws. */
    fun breadcrumb(message: String) {
        val file = File(dir ?: return, TRAIL_FILE)
        try {
            file.appendText("${timestamp("HH:mm:ss.SSS")} $message\n")
            if (file.length() > 64 * 1024) file.writeText(file.readLines().takeLast(TRAIL_KEEP).joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {
            // Diagnostics must never break the app.
        }
    }

    fun record(error: Throwable, threadName: String = Thread.currentThread().name) {
        val file = File(dir ?: return, CRASH_FILE)
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        file.writeText(
            buildString {
                appendLine(device())
                appendLine("${timestamp("yyyy-MM-dd HH:mm:ss Z")} on thread $threadName")
                appendLine()
                append(trace)
            },
        )
        breadcrumb("CRASH ${error.javaClass.name}: ${error.message}")
    }

    fun read(context: Context): String? = File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText()

    fun trail(context: Context): String = File(context.filesDir, TRAIL_FILE).takeIf { it.exists() }?.readText().orEmpty()

    fun clear(context: Context) {
        File(context.filesDir, CRASH_FILE).delete()
    }

    private fun device() =
        "Clicker ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE}) on " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"

    private fun timestamp(pattern: String) = SimpleDateFormat(pattern, Locale.US).format(Date())
}
