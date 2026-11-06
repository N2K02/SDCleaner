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
 * ADB-only replacement for the legacy global-cache/Accessibility regression
 * tests. Whole-tool cleanup must never target an excluded package; explicit
 * user selections may still target it. A broken stream must never reconnect
 * once per remaining package and must retain their failure records.
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
        return InaccessibleDeleter(userManager, adb, cacheProvider, settings)
    }

    @Test fun `whole tool clean does not touch excluded app even when its cache is large`() = runTest {
        val normal = junk("com.example.normal", bytes = 10_000L)
        val limited = junk("com.example.limited", limited = true, bytes = 100_000L)
        val d = deleter()
        coEvery { cacheProvider.determineCache(normal.pkg) } returnsMany listOf(
            normal.inaccessibleCache, normal.inaccessibleCache?.copy(totalSize = 0L, itemCount = 0))
        coEvery { adb.clearCache("com.example.normal", 0) } returns
            DirectAdb.CommandResult(0, "Success")
        val result = d.deleteInaccessible(
            AppCleaner.Data(junks = listOf(limited, normal)), null, false, false)
        result.succesful shouldContain normal.identifier
        result.failed shouldNotContainKey limited.identifier
        coVerify(exactly = 1) { adb.clearCache("com.example.normal", 0) }
        coVerify(exactly = 0) { adb.clearCache("com.example.limited", any()) }
    }

    @Test fun `explicit selection retains ability to clear excluded app cache`() = runTest {
        val limited = junk("com.example.limited", limited = true)
        val d = deleter()
        coEvery { cacheProvider.determineCache(limited.pkg) } returnsMany listOf(
            limited.inaccessibleCache, limited.inaccessibleCache?.copy(totalSize = 0L, itemCount = 0))
        coEvery { adb.clearCache("com.example.limited", 0) } returns
            DirectAdb.CommandResult(0, "Success")
        val result = d.deleteInaccessible(
            AppCleaner.Data(junks = listOf(limited)), listOf(limited.identifier), false, false)
        result.succesful shouldContain limited.identifier
        coVerify(exactly = 1) { adb.clearCache("com.example.limited", 0) }
    }

    @Test fun `closed stream aborts remaining packages without per-app reconnection loop`() = runTest {
        val first = junk("com.example.first", bytes = 80_000L)
        val second = junk("com.example.second", bytes = 40_000L)
        val d = deleter()
        coEvery { cacheProvider.determineCache(first.pkg) } returns first.inaccessibleCache
        coEvery { adb.clearCache("com.example.first", 0) } throws
            DirectAdb.TransportException("Stream closed")
        val result = d.deleteInaccessible(
            AppCleaner.Data(junks = listOf(first, second)), null, false, false)
        result.failed.containsKey(first.identifier) shouldBe true
        result.failed.containsKey(second.identifier) shouldBe true
        coVerify(exactly = 1) { adb.ensureConnected() }
        coVerify(exactly = 1) { adb.clearCache("com.example.first", 0) }
        coVerify(exactly = 0) { adb.clearCache("com.example.second", any()) }
        coVerify(exactly = 1) { adb.disconnect() }
    }
}
