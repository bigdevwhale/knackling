package app.knackling.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/** Photos of the work: kept in filesDir/photos as JPEGs no bigger than 1600 px, upright. */
object Photos {
    private const val MAX = 1600

    fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** A file for the system camera to write into, and its content uri. */
    fun camera(ctx: Context): Pair<File, Uri>? = runCatching {
        val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")
        f to FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }.getOrNull()

    /** Copies a picked or shot picture in, scaled down and turned upright; the new file name, or null. */
    fun take(ctx: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val turn = ctx.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
            }
        } ?: 0f
        val s = minOf(1f, MAX.toFloat() / maxOf(raw.width, raw.height))
        val m = Matrix().apply { postScale(s, s); postRotate(turn) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        val name = UUID.randomUUID().toString().take(12) + ".jpg"
        file(ctx, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }.getOrNull()

    fun delete(ctx: Context, name: String) { if (name.isNotEmpty() && !name.contains('/')) file(ctx, name).delete() }

    /** A small decoded copy for lists and cards. */
    fun load(ctx: Context, name: String, max: Int = 600): Bitmap? {
        if (name.isEmpty()) return null
        val f = file(ctx, name); if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
        var sample = 1; while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
