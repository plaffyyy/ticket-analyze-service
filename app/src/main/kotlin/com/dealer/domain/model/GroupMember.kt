package com.dealer.domain.model

import jakarta.persistence.*
import org.springframework.data.domain.Persistable
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

enum class MemberRole {
    OWNER,
    MEMBER,
    ;

    fun toDbValue(): String = name.lowercase()
}

@Embeddable
data class GroupMemberId(
    @Column(name = "group_id", nullable = false)
    val groupId: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
) : Serializable

@Converter(autoApply = false)
class MemberRoleConverter : AttributeConverter<MemberRole, String> {
    override fun convertToDatabaseColumn(attribute: MemberRole?): String? = attribute?.toDbValue()

    override fun convertToEntityAttribute(dbData: String?): MemberRole? = dbData?.let { MemberRole.valueOf(it.uppercase()) }
}

@Entity
@Table(name = "group_members")
class GroupMember(
    @EmbeddedId
    private val id: GroupMemberId,
    @Convert(converter = MemberRoleConverter::class)
    @Column(name = "role", nullable = false, length = 10)
    var role: MemberRole = MemberRole.MEMBER,
    @Column(name = "joined_at", nullable = false, updatable = false)
    val joinedAt: OffsetDateTime = OffsetDateTime.now(),
) : Persistable<GroupMemberId> {
    // Composite key всегда задан при создании — используем Persistable чтобы
    // Spring Data JPA не путал новую запись с существующей
    @field:Transient
    private var _isNew = true

    override fun getId() = id

    override fun isNew() = _isNew

    // указываем, что после persist или загрузки бд
    // что объект уже не новый и нужно уже делать update
    @PostPersist
    @PostLoad
    fun markNotNew() {
        _isNew = false
    }
}
