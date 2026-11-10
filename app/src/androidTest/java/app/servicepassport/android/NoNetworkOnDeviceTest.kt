package app.servicepassport.android

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.InetSocketAddress
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** On real Android: the installed app holds no INTERNET permission, so the system refuses any connection it tries. */
@RunWith(AndroidJUnit4::class)
class NoNetworkOnDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun theInstalledAppCannotOpenANetworkConnection() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission("android.permission.INTERNET"))
        val refused = try { Socket().use { it.connect(InetSocketAddress("8.8.8.8", 53), 3000) }; false } catch (_: Exception) { true }
        assertTrue("a connection was possible although the app must have no network access", refused)
    }
}
