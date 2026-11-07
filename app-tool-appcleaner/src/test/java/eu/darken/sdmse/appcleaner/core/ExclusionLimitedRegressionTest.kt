package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCacheProvider
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.adb.local.DirectAdb
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.common.user.UserManager2
import eu.darken.sdmse.common.user.UserProfile2
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.mockDataStoreValue

/**
 * Exclusion semantics for the device-wide ADB trim. The trim reaches excluded
 * apps regardless, so an exclusion-limited junk may be credited when its cache
 * dropped, but it is never reported as a failed attempt. A broken stream fails
 * the run once, without per-package reconnects.
 */
class ExclusionLimitedRegressionTest : BaseTest() {
    private val handle = UserHandle2(handleId = 0)
    private val user = UserProfile2(handle = handle)
    private fun id(packageName: String) =
        InstallId(pkgId = Pkg.Id(name = packageName), userHandle = handle)

    private fun junk(packageName: String, limited: Boolean = false, bytes: Long = 50_000L) =
        previewAppJunk(
            pkg = previewInstalled(pkgName = packageName, label = packageName),
            expendables = null,
            inaccessibleCache = InaccessibleCache(
                identifier = id(packageName), isSystemApp = false,
                itemCount = 1, totalSize = bytes, publicSize = null,
                theoreticalPaths = emptySet(),
            ),
            isExclusionLimited = limited,
        )

    private val adb = mockk<DirectAdb>()
    private val cacheProvider = mockk<InaccessibleCacheProvider>()
    private val settings = mockk<AppCleanerSettings>()
    private val userManager = mockk<UserManager2>()
    private fun deleter(): InaccessibleDeleter {
        coEvery { userManager.currentUser() } returns user
        every { settings.forceStopBeforeClearing } returns mockDataStoreValue(false)
        coEvery { adb.ensureConnected() } returns true
        coJustRun { adb.disconnect() }
        coEvery { adb.trimCaches() } returns DirectAdb.CommandResult(0, "")
        return InaccessibleDeleter(userManager, adb, cacheProvider, settings)
    }

    @Test fun `excluded app is never reported as a failed attempt`() = runTest {
        val normal = junk("com.example.normal", bytes = 10_000L)
        val limited = junk("com.example.limited", limited = true, bytes = 100_000L)
        val d = deleter()
        coEvery { cacheProvider.determineCache(normal.pkg) } returns normal.inaccessibleCache?.copy(totalSize = 0L)
        coEvery { cacheProvider.determineCache(limited.pkg) } returns limited.inaccessibleCache
        val result = d.deleteInaccessible(
            AppCleaner.Data(junks = listOf(limited, normal)), null, false, false)
        result.succesful shouldContain normal.identifier
        result.failed shouldNotContainKey limited.identifier
        coVerify(exactly = 1) { adb.trimCaches() }
    }

    @Test fun `explicit selection of an excluded app does not trigger the device-wide trim`() = runTest {
        val limited = junk("com.example.limited", limited = true)
        val d = deleter()
        val result = d.deleteInaccessible(
            AppCleaner.Data(junks = listOf(limited)), listOf(limited.identifier), false, false)
        result.failed.containsKey(limited.identifier) shouldBe true
        coVerify(exactly = 0) { adb.trimCaches() }
    }

    @Test fun `closed stream fails the run once without per-app reconnection loop`() = runTest {
        val first = junk("com.example.first", bytes = 80_000L)
        val second = junk("com.example.second", bytes = 40_000L)
        val d = deleter()
        coEvery { adb.trimCaches() } throws DirectAdb.TransportException("Stream closed")
        val result = d.deleteInaccessible(
            AppCleaner.Data(junks = listOf(first, second)), null, false, false)
        result.failed.containsKey(first.identifier) shouldBe true
        result.failed.containsKey(second.identifier) shouldBe true
        coVerify(exactly = 1) { adb.ensureConnected() }
        coVerify(exactly = 1) { adb.trimCaches() }
        coVerify(exactly = 1) { adb.disconnect() }
    }
}
