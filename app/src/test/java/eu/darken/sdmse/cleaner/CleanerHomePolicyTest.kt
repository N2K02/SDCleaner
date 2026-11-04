package eu.darken.sdmse.cleaner

import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.cards.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CleanerHomePolicyTest {
    @Test fun `home admits only cleaning tools even if another module produces a tool card`() {
        val expected = setOf(SDMTool.Type.CORPSEFINDER, SDMTool.Type.SYSTEMCLEANER, SDMTool.Type.APPCLEANER, SDMTool.Type.DEDUPLICATOR)
        SDMTool.Type.entries.forEach { type ->
            val item = mockk<ToolDashboardCardItem>()
            every { item.toolType } returns type
            assertEquals(type in expected, isCleanerHomeItem(item), type.name)
        }
    }
    @Test fun `scheduling setup and access errors remain reachable without promotional cards`() {
        assertTrue(isCleanerHomeItem(mockk<SchedulerDashboardCardItem>()))
        assertTrue(isCleanerHomeItem(mockk<SetupDashboardCardItem>()))
        assertTrue(isCleanerHomeItem(mockk<ErrorDataAreaDashboardCardItem>()))
        assertFalse(isCleanerHomeItem(mockk<UpgradeDashboardCardItem>()))
        assertFalse(isCleanerHomeItem(mockk<ReviewDashboardCardItem>()))
        assertFalse(isCleanerHomeItem(mockk<AnalyzerDashboardCardItem>()))
    }
}
