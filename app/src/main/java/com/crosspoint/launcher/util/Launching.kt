package com.crosspoint.launcher.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.crosspoint.launcher.data.EPUB_MIME
import java.io.File

object Launching {

    fun launchApp(context: Context, packageName: String, activityName: String) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fall back to the package's default launch intent.
            context.packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(it)
            } ?: toast(context, "Can't open app")
        }
    }

    fun launchPackage(context: Context, packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(it)
        } ?: toast(context, "App not found")
    }

    private fun bookUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun bookMimeType(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".fb2.zip") || name.endsWith(".zip") -> "application/x-zip-compressed"
            name.endsWith(".fb2") -> "application/x-fictionbook+xml"
            name.endsWith(".epub") -> EPUB_MIME
            else -> "*/*"
        }
    }

    private fun getMimeTypesForFile(file: File): List<String> {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".fb2.zip") -> listOf(
                "application/x-zip-compressed",
                "application/zip",
                "application/x-fictionbook+xml",
                "application/x-fb2",
                "*/*"
            )
            name.endsWith(".fb2") -> listOf(
                "application/x-fictionbook+xml",
                "application/x-fictionbook",
                "application/x-fb2",
                "text/xml",
                "*/*"
            )
            name.endsWith(".zip") -> listOf(
                "application/zip",
                "application/x-zip-compressed",
                "*/*"
            )
            name.endsWith(".epub") -> listOf(
                EPUB_MIME,
                "*/*"
            )
            else -> listOf("*/*")
        }
    }

    private fun baseViewIntent(context: Context, file: File, mimeType: String = bookMimeType(file)): Intent {
        val uri = bookUri(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Open a book with the user's chosen default reader, if installed; else use the built-in reader. */
    fun openBook(context: Context, filePath: String, defaultReaderPackage: String?) {
        val file = File(filePath)
        if (!file.exists()) { toast(context, "File missing"); return }
        if (defaultReaderPackage == null) {
            context.startActivity(Intent(context, com.crosspoint.launcher.reader.ReaderActivity::class.java)
                .putExtra("book_path", filePath).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val mimeTypes = getMimeTypesForFile(file)
        var launched = false
        for (mime in mimeTypes) {
            val intent = baseViewIntent(context, file, mime)
            if (isResolvable(context, intent, defaultReaderPackage)) {
                intent.setPackage(defaultReaderPackage)
                val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
                if (ok) {
                    launched = true
                    break
                }
            }
        }
        if (!launched) {
            openBookChooser(context, filePath)
        }
    }

    /** Always present the system chooser of reader apps. */
    fun openBookChooser(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) { toast(context, "File missing"); return }
        val intent = baseViewIntent(context, file)
        val chooser = Intent.createChooser(intent, "Open with").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure { toast(context, "No reader app installed") }
    }

    private fun isResolvable(context: Context, intent: Intent, pkg: String): Boolean {
        val probe = Intent(intent).setPackage(pkg)
        return probe.resolveActivity(context.packageManager) != null
    }

    fun openManageStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    fun openHomeSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
            .onFailure { toast(context, "Can't open app info") }
    }

    fun openCalendar(context: Context) {
        val pm = context.packageManager
        // Prefer a real calendar app (CATEGORY_APP_CALENDAR), then a calendar content viewer.
        // Only launch intents that actually resolve to an activity so we don't fall through to
        // an unrelated handler (some devices route the content URI to a file viewer that then
        // reports "file not found").
        val candidates = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time").build())
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = intent.resolveActivity(pm)
            if (resolved != null && resolved.packageName != "android") {
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
        }
        toast(context, "No Default Calendar App Installed")
    }

    private fun toast(context: Context, msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
