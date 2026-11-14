package eu.darken.sdmse.scheduler.ui.manager.tour

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.tour.GuidedTour
import eu.darken.sdmse.common.compose.tour.TourDefinition
import eu.darken.sdmse.common.compose.tour.TourId
import eu.darken.sdmse.common.compose.tour.TourStep
import eu.darken.sdmse.scheduler.R

object SchedulerManagerTour : GuidedTour {

    override val id: TourId = TourId("tour.scheduler.manager")

    const val ADD_TARGET = "scheduler.add"

    fun definition(): TourDefinition = TourDefinition(
        id = id,
        clickProtection = true,
        steps = listOf(
            TourStep(
                stepId = "intro",
                targetId = null,
                body = R.string.tour_scheduler_intro_body.toCaString(),
            ),

        ),
    )
}
