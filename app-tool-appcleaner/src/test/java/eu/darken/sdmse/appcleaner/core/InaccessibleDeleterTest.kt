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

/** uid-2000 device-wide trim backend; no Accessibility, settings automation or per-app pm clear. */
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
        coEvery { adb.trimCaches() } returns DirectAdb.CommandResult(0, "")
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

    private fun run(vararg items: AppJunk, targets: Collection<InstallId>? = null) =
        kotlinx.coroutines.test.runTest {
            result = deleter.deleteInaccessible(AppCleaner.Data(junks = items.toList()), targets, false, false)
        }
    private lateinit var result: InaccessibleDeleter.InaccDelResult

    @Test fun `without an authorized ADB session only failures are reported`() {
        val item = junk("com.example.test")
        coEvery { adb.ensureConnected() } returns false
        run(item)
        result.failed shouldContainKey item.identifier
        result.succesful shouldNotContain item.identifier
        coVerify(exactly = 0) { adb.trimCaches() }
        coVerify(exactly = 1) { adb.disconnect() }
    }

    @Test fun `whole-tool clean issues exactly one trim and verifies each app`() {
        val a = junk("com.example.a", 500L)
        val b = junk("com.example.b", 300L)
        coEvery { provider.determineCache(a.pkg) } returns a.inaccessibleCache!!.copy(totalSize = 100L)
        coEvery { provider.determineCache(b.pkg) } returns b.inaccessibleCache!!.copy(totalSize = 0L)
        run(a, b)
        result.succesful shouldContain a.identifier
        result.succesful shouldContain b.identifier
        result.freedBytes[a.identifier] shouldBe 400L
        result.freedBytes[b.identifier] shouldBe 300L
        coVerify(exactly = 1) { adb.trimCaches() }
        coVerify(exactly = 1) { adb.disconnect() }
    }

    @Test fun `unchanged cache after trim is a failure, not a success`() {
        val item = junk("com.example.busy", 500L)
        coEvery { provider.determineCache(item.pkg) } returns item.inaccessibleCache
        run(item)
        result.failed shouldContainKey item.identifier
        result.succesful shouldNotContain item.identifier
    }

    @Test fun `rejected trim reports failures without verification`() {
        val item = junk("com.example.denied")
        coEvery { adb.trimCaches() } returns DirectAdb.CommandResult(255, "Error: SecurityException")
        run(item)
        result.failed shouldContainKey item.identifier
        coVerify(exactly = 0) { provider.determineCache(any()) }
    }

    @Test fun `transport failure on trim fails the run once without retries`() {
        val first = junk("com.example.first")
        val second = junk("com.example.second")
        coEvery { adb.trimCaches() } throws DirectAdb.TransportException("ADB stream timeout")
        run(first, second)
        result.failed shouldContainKey first.identifier
        result.failed shouldContainKey second.identifier
        coVerify(exactly = 1) { adb.trimCaches() }
        coVerify(exactly = 1) { adb.disconnect() }
    }

    @Test fun `targeted clean never trims every app`() {
        val item = junk("com.example.selected")
        run(item, targets = listOf(item.identifier))
        (result.failed[item.identifier] is InaccessibleDeleter.TargetedClearUnsupportedException) shouldBe true
        coVerify(exactly = 0) { adb.trimCaches() }
        coVerify(exactly = 0) { adb.ensureConnected() }
    }

    @Test fun `cancellation during trim preserves partial results and closes transport`() {
        val item = junk("com.example.z", 500L)
        coEvery { adb.trimCaches() } throws CancellationException("Quit")
        var partial: InaccessibleDeleter.InaccDelResult? = null
        kotlinx.coroutines.test.runTest {
            try {
                deleter.deleteInaccessible(
                    AppCleaner.Data(junks = listOf(item)), null, false, false,
                    onPartialResult = { partial = it },
                )
                error("Expected cancellation")
            } catch (_: CancellationException) { }
        }
        partial!!.succesful shouldNotContain item.identifier
        coVerify { adb.disconnect() }
    }

    @Test fun `trim command requests every cache byte and never data clear`() {
        val cmd = DirectAdb.trimCachesCommand()
        cmd shouldBe "pm trim-caches ${Long.MAX_VALUE}"
        cmd.contains("pm clear") shouldBe false
    }
}
