package com.dealer.domain.model

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

enum class Platform {
    IOS,
    ANDROID,
    ;

    fun toDbValue(): String = name.lowercase()
}

@Converter(autoApply = false)
class PlatformConverter : AttributeConverter<Platform, String> {
    override fun convertToDatabaseColumn(attribute: Platform?): String? = attribute?.toDbValue()

    override fun convertToEntityAttribute(dbData: String?): Platform? = dbData?.let { Platform.valueOf(it.uppercase()) }
}

@Entity
@Table(name = "devices")
class Device(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @Column(name = "fcm_token", nullable = false)
    var fcmToken: String,
    @Convert(converter = PlatformConverter::class)
    @Column(name = "platform", nullable = false, length = 10)
    var platform: Platform,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
