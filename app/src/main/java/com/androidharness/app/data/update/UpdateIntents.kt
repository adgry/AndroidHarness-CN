package com.androidharness.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Activity-independent install plumbing for [UpdateManager]: content-URI
 * handoff to the platform package installer and the unknown-sources settings
 * hop. Kept separate from the manager so the manager stays testable.
 */
object UpdateIntents {

    /** Opens the system package installer for the staged [apk]. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update",
            apk,
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun installApk(context: Context, apk: File) {
        context.startActivity(installIntent(context, apk).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun unknownSourcesIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"),
    )

    fun openUnknownSourcesSettings(context: Context) {
        context.startActivity(unknownSourcesIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
