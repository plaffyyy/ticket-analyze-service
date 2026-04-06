package com.dealer.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "bill_items")
class BillItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    val bill: Bill,
    @Column(name = "name", nullable = false, length = 255)
    var name: String,
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    var price: BigDecimal,
    @Column(name = "quantity", nullable = false)
    var quantity: Int = 1,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    lateinit var id: UUID
}
