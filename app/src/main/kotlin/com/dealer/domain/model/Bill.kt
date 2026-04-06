package com.dealer.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class BillStatus {
    OPEN,
    SETTLED,
    PROCESSING_OCR,
    ;

    fun toDbValue(): String =
        when (this) {
            OPEN -> "open"
            SETTLED -> "settled"
            PROCESSING_OCR -> "processing_ocr"
        }
}

@Converter(autoApply = false)
class BillStatusConverter : AttributeConverter<BillStatus, String> {
    override fun convertToDatabaseColumn(attribute: BillStatus?): String? = attribute?.toDbValue()

    override fun convertToEntityAttribute(dbData: String?): BillStatus? =
        dbData?.let {
            when (it) {
                "open" -> BillStatus.OPEN
                "settled" -> BillStatus.SETTLED
                "processing_ocr" -> BillStatus.PROCESSING_OCR
                else -> throw IllegalArgumentException("Unknown BillStatus: $it")
            }
        }
}

@Entity
@Table(name = "bills")
class Bill(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: Group,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    val createdBy: User,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    var total: BigDecimal = BigDecimal.ZERO,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "RUB",
    @Column(name = "receipt_url")
    var receiptUrl: String? = null,
    @Convert(converter = BillStatusConverter::class)
    @Column(name = "status", nullable = false, length = 20)
    var status: BillStatus = BillStatus.OPEN,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spun_winner")
    var spunWinner: User? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
