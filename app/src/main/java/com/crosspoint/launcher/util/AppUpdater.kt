package com.crosspoint.launcher.util

import android.content.Context
import java.io.File

/**
 * Official server update checks are disabled for this custom build.
 */
object AppUpdater {
    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val sha256: String)

    /** Always returns null so no official update checks are executed. */
    suspend fun check(): Release? = null

    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File {
        error("Update service is disabled for custom builds")
    }
}
