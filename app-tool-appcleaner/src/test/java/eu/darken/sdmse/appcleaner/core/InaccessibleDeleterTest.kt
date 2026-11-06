package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue

/** Only the actual uid-2000 backend is tested; no Accessibility or settings automation remains. */
class InaccessibleDeleterTest : BaseTest() {
    private val handle = UserHandle2(handleId = 0)
    private val profile = UserProfile2(handle = handle)
    @MockK lateinit var userManager: UserManager2
    @MockK lateinit var adb: DirectAdb
    @MockK lateinit var provider: InaccessibleCacheProvider
    @MockK lateinit var settings: AppCleanerSettings
    private lateinit var deleter: InaccessibleDeleter

    @BeforeEach fun setup() {
        MockKAnnotations.init(this)
        coEvery { userManager.currentUser() } returns profile
        coEvery { adb.ensureConnected() } returns true
        coEvery { adb.disconnect() } returns Unit
        coEvery { adb.clearCache(any(), any()) } returns DirectAdb.CommandResult(0, "Success")
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)
        deleter = InaccessibleDeleter(userManager, adb, provider, settings)
    }

    private fun junk(name: String, bytes: Long = 500L): AppJunk {
        val id = InstallId(Pkg.Id(name), handle)
        val pkg = mockk<Installed>()
        every { pkg.id } returns Pkg.Id(name)
        every { pkg.userHandle } returns handle
        every { pkg.installId } returns id
        every { pkg.packageName } returns name
        every { pkg.label } returns null
        return AppJunk(
            pkg = pkg, userProfile = profile, expendables = null,
            inaccessibleCache = InaccessibleCache(
                identifier = id, isSystemApp = false, itemCount = 1,
                totalSize = bytes, publicSize = 0L, theoreticalPaths = emptySet()
            )
        )
    }

    @Test fun `without an authorized ADB session only failures are reported`() = runTest {
        val item = junk("com.example.test")
        coEvery { adb.ensureConnected() } returns false
        val result = deleter.deleteInaccessible(
            AppCleaner.Data(junks = listOf(item)), null, false, false
        )
        result.failed shouldContainKey item.identifier
        result.succesful shouldNotContain item.identifier
        coVerify(exactly = 0) { adb.clearCache(any(), any()) }
        coVerify(exactly = 1) { adb.disconnect() }
    }

    @Test fun `cache already empty does not issue a destructive command`() = runTest {
        val item = junk("com.example.empty")
        coEvery { provider.determineCache(item.pkg) } returns item.inaccessibleCache!!.copy(totalSize = 0L)
        val result = deleter.deleteInaccessible(
            AppCleaner.Data(junks = listOf(item)), null, false, false
        )
        result.succesful shouldContain item.identifier
        result.freedBytes[item.identifier] shouldBe 0L
        coVerify(exactly = 0) { adb.clearCache(any(), any()) }
    }

    @Test fun `success requires observed cache decrease`() = runTest {
        val item = junk("com.example.success", 500L)
        coEvery { provider.determineCache(item.pkg) } returnsMany listOf(
            item.inaccessibleCache!!,
            item.inaccessibleCache!!.copy(totalSize = 100L)
        )
        val result = deleter.deleteInaccessible(
            AppCleaner.Data(junks = listOf(item)), null, false, false
        )
        result.succesful shouldContain item.identifier
        result.freedBytes[item.identifier] shouldBe 400L
        coVerify(exactly = 1) { adb.clearCache("com.example.success", 0) }
    }

    @Test fun `platform command failure does not report success`() = runTest {
        val item = junk("com.example.denied")
        coEvery { provider.determineCache(item.pkg) } returns item.inaccessibleCache
        coEvery { adb.clearCache(any(), any()) } returns DirectAdb.CommandResult(1, "Failure")
        val result = deleter.deleteInaccessible(
            AppCleaner.Data(junks = listOf(item)), null, false, false
        )
        result.failed shouldContainKey item.identifier
        result.succesful shouldNotContain item.identifier
    }

    @Test fun `ADB stream failure skips remaining packages without repeated 15-second reconnects`() = runTest {
        val first = junk("com.example.first", 800L)
        val second = junk("com.example.second", 700L)
        val third = junk("com.example.third", 600L)
        listOf(first, second, third).forEach { item ->
            coEvery { provider.determineCache(item.pkg) } returns item.inaccessibleCache
        }
        coEvery { adb.clearCache("com.example.first", 0) } throws
            DirectAdb.TransportException("ADB stream timeout")
        val result = deleter.deleteInaccessible(
            AppCleaner.Data(junks = listOf(first, second, third)), null, false, true
        )
        result.failed shouldContainKey first.identifier
        result.failed shouldContainKey second.identifier
        result.failed shouldContainKey third.identifier
        result.succesful shouldNotContain first.identifier
        coVerify(exactly = 1) { adb.clearCache("com.example.first", 0) }
        coVerify(exactly = 0) { adb.clearCache("com.example.second", 0) }
        coVerify(exactly = 0) { adb.clearCache("com.example.third", 0) }
        coVerify(exactly = 1) { adb.disconnect() }
    }

    @Test fun `cancellation preserves partial results and closes transport`() = runTest {
        val good = junk("com.example.a", 600L)
        val interrupted = junk("com.example.z", 500L)
        coEvery { provider.determineCache(good.pkg) } returnsMany listOf(
            good.inaccessibleCache!!, good.inaccessibleCache!!.copy(totalSize = 0L)
        )
        coEvery { provider.determineCache(interrupted.pkg) } returns interrupted.inaccessibleCache
        coEvery { adb.clearCache("com.example.z", 0) } throws CancellationException("Quit")
        var partial: InaccessibleDeleter.InaccDelResult? = null
        try {
            deleter.deleteInaccessible(
                AppCleaner.Data(junks = listOf(good, interrupted)), null, false, false,
                onPartialResult = { partial = it },
            )
            error("Expected cancellation")
        } catch (_: CancellationException) { }
        partial!!.succesful shouldContain good.identifier
        coVerify { adb.disconnect() }
    }
}
