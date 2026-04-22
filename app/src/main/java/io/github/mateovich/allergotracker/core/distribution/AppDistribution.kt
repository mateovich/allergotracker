package io.github.mateovich.allergotracker.core.distribution

import io.github.mateovich.allergotracker.BuildConfig

data class AppDistribution(
    val channel: String,
    val supportsStoreServices: Boolean,
    val unavailableCapabilityMessage: String?
) {
    val distributionNotice: String =
        "This build keeps capture, review, export, and import available without Play or store services."
}

object AppDistributionFactory {
    fun fromBuildConfig(): AppDistribution {
        return AppDistribution(
            channel = BuildConfig.DISTRIBUTION_CHANNEL,
            supportsStoreServices = BuildConfig.SUPPORTS_STORE_SERVICES,
            unavailableCapabilityMessage = BuildConfig.UNAVAILABLE_CAPABILITY_MESSAGE
        )
    }
}
