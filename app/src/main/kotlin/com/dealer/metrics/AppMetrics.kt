package com.dealer.metrics

import com.dealer.repository.GroupRepository
import com.dealer.repository.UserRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class AppMetrics(
    meterRegistry: MeterRegistry,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
) {
    private val userRegistrationsTotal: Counter =
        Counter
            .builder("dealer_user_registrations_total")
            .description("Total number of successful user registrations")
            .register(meterRegistry)

    private val userProfileUpdatesTotal: Counter =
        Counter
            .builder("dealer_user_profile_updates_total")
            .description("Total number of successful user profile updates")
            .register(meterRegistry)

    private val groupCreationsTotal: Counter =
        Counter
            .builder("dealer_group_creations_total")
            .description("Total number of successful group creations")
            .register(meterRegistry)

    private val groupUpdatesTotal: Counter =
        Counter
            .builder("dealer_group_updates_total")
            .description("Total number of successful group updates")
            .register(meterRegistry)

    init {
        Gauge
            .builder("dealer_users_total", userRepository) { repository -> repository.count().toDouble() }
            .description("Current total number of users")
            .register(meterRegistry)

        Gauge
            .builder("dealer_groups_total", groupRepository) { repository -> repository.count().toDouble() }
            .description("Current total number of groups")
            .register(meterRegistry)
    }

    fun incrementUserRegistrations() {
        userRegistrationsTotal.increment()
    }

    fun incrementUserProfileUpdates() {
        userProfileUpdatesTotal.increment()
    }

    fun incrementGroupCreations() {
        groupCreationsTotal.increment()
    }

    fun incrementGroupUpdates() {
        groupUpdatesTotal.increment()
    }
}
