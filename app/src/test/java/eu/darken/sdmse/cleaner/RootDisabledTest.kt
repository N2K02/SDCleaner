package eu.darken.sdmse.cleaner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.RootSettings
import eu.darken.sdmse.common.root.RootUnavailableException
import eu.darken.sdmse.common.root.service.RootServiceClient
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class RootDisabledTest : BaseTest() {
    @Test
    fun `restored root opt-in is ignored and refresh never probes for root`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = RootSettings(context)
        settings.useRoot.value(true)
        try {
            val client = mockk<RootServiceClient>()
            val manager = RootManager(client)
            repeat(3) {
                manager.refresh()
                manager.isRooted() shouldBe false
                manager.isInstalled() shouldBe false
                manager.useRoot.first() shouldBe false
                manager.accessState.first() shouldBe AccessState.Declined
            }
            confirmVerified(client)
        } finally {
            settings.useRoot.value(null)
        }
    }

    @Test
    fun `direct root service requests fail instead of launching su`(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val client = RootServiceClient(scope, TestDispatcherProvider(Dispatchers.Unconfined))
            var rejected = false
            try {
                withTimeout(2000) { client.get().use { error("Root connection must never be available") } }
            } catch (_: RootUnavailableException) {
                rejected = true
            }
            rejected shouldBe true
        } finally {
            scope.cancel()
        }
    }
}
