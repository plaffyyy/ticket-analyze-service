package com.dealer.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "groups")
class Group(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    var inviteCode: String,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "RUB",
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
