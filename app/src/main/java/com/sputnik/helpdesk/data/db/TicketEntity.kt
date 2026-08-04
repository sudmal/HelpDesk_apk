package com.sputnik.helpdesk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val id: Int,
    val number: String,
    val createdAt: String?,
    val scheduledAt: String?,
    val closedAt: String?,
    val description: String?,
    val phone: String?,
    val apartment: String?,
    val closeNotes: String?,
    val closedBy: String?,
    val actNumber: String?,
    val actId: Int? = null,
    val actStatus: String? = null,
    val actMaterialsChangedAt: String? = null,
    val addressFull: String?,
    val territoryId: Int? = null,
    val territoryName: String? = null,
    val typeName: String?,
    val serviceTypeName: String?,
    val serviceTypeColor: String?,
    val statusName: String?,
    val statusColor: String?,
    val statusIsFinal: Boolean,
    val statusSlug: String?,
    val brigade: String?,
    val assignee: String?,
    val commentsJson: String,
    val group: String,
    val isNew: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
)
