package eu.darken.sdmse.scheduler.ui.manager.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SchedulerManagerTourTest : BaseTest() {

    @Test
    fun `definition explains automatic schedule without add action`() {
        val def = SchedulerManagerTour.definition()
        def.id shouldBe TourId("tour.scheduler.manager")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("intro")
        // No add action exists in the single-schedule screen.
        def.steps.map { it.targetId } shouldBe listOf(null)
    }
}
