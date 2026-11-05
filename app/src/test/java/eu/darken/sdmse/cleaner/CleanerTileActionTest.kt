package eu.darken.sdmse.cleaner

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CleanerTileActionTest {
    @Test fun `idle card shows explicit Scan button`() {
        cleanerTileAction(false, false, false) shouldBe CleanerTileAction.SCAN
    }

    @Test fun `completed scan shows Review and Clean buttons`() {
        cleanerTileAction(false, false, true) shouldBe CleanerTileAction.REVIEW_AND_CLEAN
    }

    @Test fun `active scanning exposes Cancel not Clean`() {
        cleanerTileAction(false, true, true) shouldBe CleanerTileAction.CANCEL
    }

    @Test fun `initializing card does not expose actions`() {
        cleanerTileAction(true, false, true) shouldBe CleanerTileAction.INITIALIZING
    }
}
