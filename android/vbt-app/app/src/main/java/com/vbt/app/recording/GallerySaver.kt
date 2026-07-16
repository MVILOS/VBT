package com.vbt.app.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Zapisuje gotowy (z wypaloną nakładką) plik wideo do galerii, do katalogu
 * Movies/VBT. Na Androidzie 10+ (API 29) używa MediaStore z RELATIVE_PATH i
 * IS_PENDING - bez uprawnień do pamięci. Na 26-28 pisze do publicznego katalogu
 * Movies (wymaga WRITE_EXTERNAL_STORAGE, maxSdkVersion=28) i odświeża MediaScanner.
 */
class GallerySaver(private val context: Context) {

    companion object {
        private const val ALBUM = "VBT"
    }

    suspend fun save(source: File, displayName: String): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(source, displayName)
        } else {
            saveLegacy(source, displayName)
        }
    }

    private fun saveViaMediaStore(source: File, displayName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore.insert zwrócił null - nie udało się utworzyć wpisu w galerii")

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: error("Nie udało się otworzyć strumienia zapisu dla $uri")

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveLegacy(source: File, displayName: String): Uri {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val albumDir = File(moviesDir, ALBUM).apply { mkdirs() }
        val dest = File(albumDir, displayName)
        source.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }

        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
        return Uri.fromFile(dest)
    }
}
