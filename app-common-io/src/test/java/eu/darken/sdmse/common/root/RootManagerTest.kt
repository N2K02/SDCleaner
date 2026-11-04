package eu.darken.sdmse.common.root

import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.root.service.RootServiceClient
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class RootManagerTest {
    @Test
    fun `root remains unavailable across refreshes without contacting a service`() = runBlocking {
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
    }
}
