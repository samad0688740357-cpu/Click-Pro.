package id.biz.rrndev.autoclicker.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/** Helpers for JSON file import/export. */
object JsonUtils {

    private const val EXPORT_FILE = "autoclicker_flows.json"

    /**
     * Write [json] to a file in the app's cache dir and return a shareable [Uri]
     * via FileProvider.
     */
    fun shareJson(context: Context, json: String, fileName: String = EXPORT_FILE): Intent {
        val file = File(context.cacheDir, fileName)
        file.writeText(json)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Read the content from a [Uri] returned by a file picker.
     * Returns null if the uri cannot be opened.
     */
    fun readJsonFromUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }
    }.getOrNull()
}
