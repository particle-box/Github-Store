package zed.rainxch.githubstore.feature.details.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import zed.rainxch.githubstore.feature.install.DownloadProgress
import zed.rainxch.githubstore.feature.install.Downloader
import zed.rainxch.githubstore.feature.install.FileLocationsProvider

class AndroidDownloader(
    private val context: Context,
    private val files: FileLocationsProvider
) : Downloader {

    private val downloadManager by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    override fun download(url: String, suggestedFileName: String?): Flow<DownloadProgress> = flow {
        val dirPath = files.appDownloadsDir()
        val dir = File(dirPath)
        if (!dir.exists()) dir.mkdirs()

        val safeName = (suggestedFileName?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').ifBlank { "asset-${UUID.randomUUID()}" })

        val tentativeDestination = File(dir, safeName)

        if (tentativeDestination.exists() && tentativeDestination.length() > 0) {
            emit(
                DownloadProgress(
                    tentativeDestination.length(),
                    tentativeDestination.length(),
                    100
                )
            )
            return@flow
        }

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(safeName)
            setDescription("Downloading asset")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            setDestinationInExternalFilesDir(context, "ghs_downloads", safeName)
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val downloadId = downloadManager.enqueue(request)

        var isDone = false
        while (!isDone && currentCoroutineContext().isActive) {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val percent = if (total > 0) ((downloaded * 100L) / total).toInt() else null

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val localUriStr = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        val finalPath = Uri.parse(localUriStr).path ?: throw IllegalStateException("Invalid local URI: $localUriStr")

                        val file = File(finalPath)

                        var attempts = 0
                        while ((!file.exists() || file.length() == 0L) && attempts < 6) {  // ~3s max
                            delay(500L)
                            attempts++
                            emit(DownloadProgress(downloaded, total, 100))  // For UI "preparing"
                        }
                        if (!file.exists() || file.length() == 0L) {
                            throw IllegalStateException("File not ready after timeout: $finalPath")
                        }

                        emit(DownloadProgress(downloaded, total, 100))
                        isDone = true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        throw IllegalStateException("Download failed: $reason")
                    }
                    else -> emit(
                        DownloadProgress(
                            downloaded,
                            if (total >= 0) total else null,
                            percent
                        )
                    )
                }
            } else {
                throw IllegalStateException("Download ID not found: $downloadId")
            }
            cursor.close()

            if (!isDone) delay(500L)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun saveToFile(url: String, suggestedFileName: String?): String = withContext(Dispatchers.IO) {
        download(url, suggestedFileName).last()
        val safeName = (suggestedFileName?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').ifBlank { "asset-${UUID.randomUUID()}" })
        File(files.appDownloadsDir(), safeName).absolutePath
    }
}