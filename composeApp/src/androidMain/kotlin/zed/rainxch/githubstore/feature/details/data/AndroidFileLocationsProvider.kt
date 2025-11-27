package zed.rainxch.githubstore.feature.details.data

import android.content.Context
import zed.rainxch.githubstore.feature.install.FileLocationsProvider
import java.io.File

class AndroidFileLocationsProvider(
    private val context: Context
) : FileLocationsProvider {
    override fun appDownloadsDir(): String {
        val externalFilesRoot = context.getExternalFilesDir(null)
        val dir = File(externalFilesRoot, "ghs_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    override fun setExecutableIfNeeded(path: String) {
        // No-op on Android
    }
}