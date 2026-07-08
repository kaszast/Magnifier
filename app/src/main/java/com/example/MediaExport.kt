package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

// Helper to save Bitmap to the Device's Public/Scoped Gallery
fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val filename = "Nagyito_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nagyító")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            return imageUri
        } catch (e: Exception) {
            Log.e("Magnifier", "Sikertelen kép mentés", e)
            resolver.delete(imageUri, null, null)
        }
    }
    return null
}

// Helper to share Captured Bitmap via standard Android ACTION_SEND Intent and FileProvider URI
fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "shared_images")
        cachePath.mkdirs()
        
        // Clean old cached shared files to save user disk space
        cachePath.listFiles()?.forEach { it.delete() }
        
        val file = File(cachePath, "magnifier_share_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/jpeg"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Fénykép megosztása"))
        }
    } catch (e: Exception) {
        Log.e("Magnifier", "Sikertelen kép megosztás", e)
    }
}
