package com.dealer.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "email", nullable = false, unique = true, length = 255)
    val email: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,
    @Column(name = "avatar_url")
    var avatarUrl: String? = null,
    @Column(name = "currency_default", nullable = false, length = 3)
    var currencyDefault: String = "RUB",
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
