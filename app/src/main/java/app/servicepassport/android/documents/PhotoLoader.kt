package app.servicepassport.android.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

/** A photo ready for text recognition: pixels as stored, and how far to turn them to stand upright. */
class LoadedPhoto(val bitmap: Bitmap, val rotationDegrees: Int)

/**
 * Photos from current phones can be far larger than text recognition needs, and decoding them whole can exhaust
 * memory. A photo is read at a size where printed text is still comfortably legible, and its stored orientation is
 * passed on so that a sideways photo is read upright.
 */
object PhotoLoader {
    /** Longest side after loading. A 12-megapixel photo stays whole; larger ones are halved until they fit. */
    const val MAX_SIDE = 4096

    /** Power of two by which both sides are divided. */
    fun sampleSize(width: Int, height: Int, maxSide: Int = MAX_SIDE): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxSide) sample *= 2
        return sample
    }

    fun rotationDegrees(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    /** @throws IOException when the file is not an image this phone can decode */
    fun load(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): LoadedPhoto {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: throw IOException("cannot open")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (_: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        var sample = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        repeat(3) {
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("cannot decode")
                return LoadedPhoto(bitmap, rotationDegrees(orientation))
            } catch (_: OutOfMemoryError) {
                sample *= 2 // a smaller copy is still readable; failing would lose the document
            }
        }
        throw IOException("too large to decode")
    }
}
