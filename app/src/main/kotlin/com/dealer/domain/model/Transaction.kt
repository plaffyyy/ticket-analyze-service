package com.dealer.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class TransactionStatus {
    PENDING,
    SETTLED,
    ;

    fun toDbValue(): String = name.lowercase()
}

@Converter(autoApply = false)
class TransactionStatusConverter : AttributeConverter<TransactionStatus, String> {
    override fun convertToDatabaseColumn(attribute: TransactionStatus?): String? = attribute?.toDbValue()

    override fun convertToEntityAttribute(dbData: String?): TransactionStatus? = dbData?.let { TransactionStatus.valueOf(it.uppercase()) }
}

@Entity
@Table(name = "transactions")
class Transaction(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    val bill: Bill,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debtor_id", nullable = false)
    val debtor: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creditor_id", nullable = false)
    val creditor: User,
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,
    @Convert(converter = TransactionStatusConverter::class)
    @Column(name = "status", nullable = false, length = 10)
    var status: TransactionStatus = TransactionStatus.PENDING,
    @Column(name = "settled_at")
    var settledAt: OffsetDateTime? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
