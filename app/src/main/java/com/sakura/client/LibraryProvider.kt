package com.sakura.client

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Отдаёт Minecraft файлы из библиотеки приложения (миры, аддоны, скины).
 * Адрес: content://com.sakura.client.files/<категория>/<имя файла>. Имя файла сохраняет расширение
 * (.mcworld, .mcpack ...), по нему Minecraft понимает, что импортировать.
 */
class LibraryProvider : ContentProvider() {
    override fun onCreate() = true

    private fun fileFor(uri: Uri): File? {
        val seg = uri.pathSegments
        if (seg.size != 2) return null
        val cat = seg[0]
        val name = seg[1]
        if (cat !in CATS || name.contains('/') || name.contains("..")) return null
        val f = File(File(context!!.filesDir, "library/$cat"), name)
        return if (f.isFile) f else null
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(cols).apply { addRow(row) }
    }

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.sakura.client.files"
        val CATS = setOf("world", "addon", "skin")

        fun uriFor(cat: String, name: String): Uri =
            Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(cat).appendPath(name).build()
    }
}
