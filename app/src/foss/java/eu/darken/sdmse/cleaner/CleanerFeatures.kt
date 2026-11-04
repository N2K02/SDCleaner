package eu.darken.sdmse.cleaner

import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** This fork includes every cleaning tool; it has no purchase or supporter flow. */
class CleanerFeatures @Inject constructor() : UpgradeRepo {
    override val storeSite = "https://github.com/N2K02/SDCleaner"
    override val upgradeSite = storeSite
    override val betaSite = "$storeSite/releases"
    override val upgradeInfo = flowOf(object : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.FOSS
        override val isPro = true
        override val isSettled = true
        override val upgradedAt: java.time.Instant? = null
        override val error: Throwable? = null
    })
    override suspend fun refresh() = Unit
}
