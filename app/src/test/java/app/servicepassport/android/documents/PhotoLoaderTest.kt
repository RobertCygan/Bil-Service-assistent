package app.servicepassport.android.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real image files are written and decoded here, so the loading itself is exercised before any device sees it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoLoaderTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun photo(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, orientation: Int? = null): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }
        val file = File(context.cacheDir, "photo-${UUID.randomUUID()}.${if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"}")
        file.outputStream().use { bitmap.compress(format, 90, it) }
        if (orientation != null) ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        return Uri.fromFile(file)
    }

    @Test fun anOrdinaryPhotoIsLoadedWhole() {
        val loaded = PhotoLoader.load(context, photo(1600, 1200))
        assertEquals(1600 to 1200, loaded.bitmap.width to loaded.bitmap.height)
        assertEquals(0, loaded.rotationDegrees)
    }

    @Test fun aPhotoStoredSidewaysKeepsItsPixelsAndSaysWhichWayIsUp() {
        val loaded = PhotoLoader.load(context, photo(1600, 1200, orientation = ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(1600 to 1200, loaded.bitmap.width to loaded.bitmap.height)
        assertEquals(90, loaded.rotationDegrees)
    }

    @Test fun aPhotoOverTheLimitIsLoadedSmaller() {
        val loaded = PhotoLoader.load(context, photo(1754, 1240, orientation = ExifInterface.ORIENTATION_ROTATE_270), maxSide = 1000)
        assertEquals(877 to 620, loaded.bitmap.width to loaded.bitmap.height)
        assertEquals(270, loaded.rotationDegrees)
    }

    @Test fun aScreenshotWithoutOrientationDataIsLoadedUpright() {
        val loaded = PhotoLoader.load(context, photo(1080, 2400, Bitmap.CompressFormat.PNG))
        assertEquals(1080 to 2400, loaded.bitmap.width to loaded.bitmap.height)
        assertEquals(0, loaded.rotationDegrees)
    }

    @Test fun aFileThatIsNotAnImageIsRefused() {
        val file = File(context.cacheDir, "not-a-photo-${UUID.randomUUID()}.jpg").apply { writeText("this is not a photo") }
        assertThrows(IOException::class.java) { PhotoLoader.load(context, Uri.fromFile(file)) }
        assertThrows(IOException::class.java) { PhotoLoader.load(context, Uri.fromFile(File(context.cacheDir, "missing.jpg"))) }
    }

    @Test fun ordinaryPhotosAreReadWhole() {
        assertEquals(1, PhotoLoader.sampleSize(4000, 3000))
        assertEquals(1, PhotoLoader.sampleSize(3000, 4000))
        assertEquals(1, PhotoLoader.sampleSize(4096, 4096))
        assertEquals(1, PhotoLoader.sampleSize(1, 1))
    }

    @Test fun veryLargePhotosAreHalvedUntilTheyFit() {
        assertEquals("50 megapixels", 2, PhotoLoader.sampleSize(8160, 6120))
        assertEquals("108 megapixels", 4, PhotoLoader.sampleSize(12000, 9000))
        assertEquals("200 megapixels", 4, PhotoLoader.sampleSize(16320, 12240))
        assertEquals("a long narrow scan counts by its longest side", 8, PhotoLoader.sampleSize(100, 20000))
        assertEquals(2, PhotoLoader.sampleSize(4097, 10))
    }

    @Test fun theResultNeverExceedsTheLimit() {
        for (side in listOf(1, 999, 4096, 4097, 8192, 8193, 30000, 65535)) {
            val sample = PhotoLoader.sampleSize(side, side / 2 + 1)
            assert(side / sample <= PhotoLoader.MAX_SIDE) { "$side / $sample" }
            assert(sample == 1 || side / (sample / 2) > PhotoLoader.MAX_SIDE) { "$side is reduced more than needed" }
        }
    }

    @Test fun storedOrientationBecomesARotation() {
        assertEquals(0, PhotoLoader.rotationDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, PhotoLoader.rotationDegrees(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(90, PhotoLoader.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, PhotoLoader.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, PhotoLoader.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals("an unknown value is treated as upright", 0, PhotoLoader.rotationDegrees(42))
    }
}
