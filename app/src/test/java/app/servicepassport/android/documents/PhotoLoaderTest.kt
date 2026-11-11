package app.servicepassport.android.documents

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoLoaderTest {
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
