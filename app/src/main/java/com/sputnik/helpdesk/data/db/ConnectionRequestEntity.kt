package com.sputnik.helpdesk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sputnik.helpdesk.data.model.ConnectionRequest
import java.time.LocalDate

@Entity(tableName = "connection_requests")
data class ConnectionRequestEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val phone: String?,
    val addressString: String?,
    val description: String?,
    val status: String,
    val scheduledAt: String?,
    val notes: String?,
    val actNumber: String?,
    val actId: Int? = null,
    val actStatus: String? = null,
    val actMaterialsChangedAt: String? = null,
    val feasibility: String? = null,
    val feasibilityComment: String? = null,
    val feasibilityAt: String? = null,
    val territoryId: Int?,
    val territoryName: String?,
    val creator: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val isNew: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
)

fun ConnectionRequest.toEntity(isNew: Boolean = false): ConnectionRequestEntity = ConnectionRequestEntity(
    id                     = id,
    name                   = name,
    phone                  = phone,
    addressString          = address_string,
    description            = description,
    status                 = status,
    scheduledAt            = scheduled_at,
    notes                  = notes,
    actNumber              = act_number,
    actId                  = act?.id,
    actStatus              = act?.status,
    actMaterialsChangedAt  = act?.materials_changed_at,
    feasibility            = feasibility,
    feasibilityComment     = feasibility_comment,
    feasibilityAt          = feasibility_at,
    territoryId            = territory?.id,
    territoryName          = territory?.name,
    creator                = creator,
    createdAt              = created_at,
    updatedAt              = updated_at,
    isNew                  = isNew,
)

// "overdue"/"today"/"tomorrow" bucket for a scheduled visit date, matching the ticket tabs; null otherwise.
fun ConnectionRequestEntity.dateGroup(today: LocalDate = LocalDate.now()): String? {
    if (status != "scheduled") return null
    val date = scheduledAt?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return when {
        date.isBefore(today) -> "overdue"
        date.isEqual(today) -> "today"
        date.isEqual(today.plusDays(1)) -> "tomorrow"
        else -> null
    }
}
