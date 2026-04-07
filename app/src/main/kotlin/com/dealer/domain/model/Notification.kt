package com.dealer.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "notifications")
class Notification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @Column(name = "type", nullable = false, length = 50)
    val type: String,
    @Column(name = "title", nullable = false, length = 255)
    val title: String,
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    val body: String,
    @Column(name = "payload_json", columnDefinition = "TEXT")
    val payloadJson: String? = null,
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
