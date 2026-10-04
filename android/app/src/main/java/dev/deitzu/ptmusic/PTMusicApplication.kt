package dev.deitzu.ptmusic

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PTMusicApplication : Application() {
    companion object {
        private const val CRASH_DIR = "logs"
        private const val CRASH_FILE = "last_crash.txt"
    }

    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeCrashLog(thread, throwable)
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val directory = getExternalFilesDir(CRASH_DIR) ?: File(filesDir, CRASH_DIR)
        directory.mkdirs()

        val stackTrace = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTrace))

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS Z",
            Locale.US
        ).format(Date())

        val report = buildString {
            appendLine("PT Local Music Player crash report")
            appendLine("Timestamp: $timestamp")
            appendLine("Version: $versionName")
            appendLine("Package: $packageName")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name} (${thread.id})")
            appendLine()
            appendLine(stackTrace.toString())
        }

        File(directory, CRASH_FILE).writeText(report, Charsets.UTF_8)
    }
}
