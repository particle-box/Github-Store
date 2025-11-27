package zed.rainxch.githubstore.feature.details.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.net.toUri
import zed.rainxch.githubstore.feature.install.FileLocationsProvider
import zed.rainxch.githubstore.feature.install.Installer

class AndroidInstaller(
    private val context: Context,
    private val files: FileLocationsProvider,
) : Installer {

    override suspend fun isSupported(extOrMime: String): Boolean {
        val ext = extOrMime.lowercase()
        return ext == "apk" || ext == ".apk"
    }

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = context.packageManager
            if (!pm.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                throw IllegalStateException("Please enable 'Install unknown apps' for this app in Settings and try again.")
            }
        }
    }

    override suspend fun install(filePath: String, extOrMime: String) {
        val file = File(filePath)
        val authority = "${context.packageName}.fileprovider"
        val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            throw IllegalStateException("No installer available on this device")
        }
    }
}