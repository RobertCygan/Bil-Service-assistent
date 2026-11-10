package app.servicepassport.android.privacy

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The privacy text promises that nothing leaves the phone. That holds only while no library brings the permission back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoNetworkTest {
    @Test fun thePackagedAppDoesNotRequestInternetAccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue(requested.toString(), "android.permission.POST_NOTIFICATIONS" in requested)
        assertFalse(requested.toString(), "android.permission.INTERNET" in requested)
        assertFalse(requested.toString(), "android.permission.CAMERA" in requested)
    }
}
