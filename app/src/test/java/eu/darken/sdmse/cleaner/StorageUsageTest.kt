package eu.darken.sdmse.cleaner

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StorageUsageTest {
    @Test fun `normal device storage correctly derives usage`() {
        val storage = requireNotNull(calculateStorageUsage(256, 64))
        storage.usedBytes shouldBe 192L
        storage.freeBytes shouldBe 64L
        storage.percentUsed shouldBe 75
    }

    @Test fun `invalid total never creates fabricated usage`() {
        calculateStorageUsage(0, 0) shouldBe null
        calculateStorageUsage(-1, 0) shouldBe null
    }

    @Test fun `reported free space is safely bounded`() {
        calculateStorageUsage(100, 120)?.percentUsed shouldBe 0
        calculateStorageUsage(100, -5)?.percentUsed shouldBe 100
    }

    @Test fun `usage calculation handles large byte values without integer overflow`() {
        val result = requireNotNull(calculateStorageUsage(500_000_000_000L, 200_000_000_000L))
        result.usedBytes shouldBe 300_000_000_000L
        result.percentUsed shouldBe 60
    }
}
