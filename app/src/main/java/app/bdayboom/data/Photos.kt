package app.bdayboom.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID

/** Person photos: app-private JPEGs, square, 512 px (~50 KB). Never a content:// URI that can vanish. */
object Photos {
    private const val SIZE = 512

    fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    /**
     * Decodes [uri], centre-crops to a square and saves it. Returns the new file's absolute path.
     * shortcut: automatic centre crop, no crop screen; add an interactive cropper if users ask to frame faces.
     */
    fun importFrom(context: Context, uri: Uri): String {
        val src = decode(context, uri)
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        val file = File(dir(context), "${UUID.randomUUID()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
    }

    private fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                // decode big camera photos at a fraction of their size; we only keep 512 px
                val s = info.size
                val factor = maxOf(1, minOf(s.width, s.height) / (SIZE * 2))
                dec.setTargetSize(s.width / factor, s.height / factor)
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / (SIZE * 2))
            context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("not an image")
        }

    /** Deletes photo files no person points at (unsaved picks, replaced or purged people). */
    fun deleteOrphans(context: Context, inUse: Set<String>) {
        dir(context).listFiles()?.forEach { if (it.absolutePath !in inUse) it.delete() }
    }
}
