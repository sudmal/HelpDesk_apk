package com.sputnik.helpdesk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sputnik.helpdesk.data.model.ActListItem

@Entity(tableName = "acts")
data class ActEntity(
    @PrimaryKey val id: Int,
    val number: String,
    val type: String,
    val status: String,
    val ticketId: Int?,
    val ticketNumber: String?,
    val connectionRequestId: Int?,
    val connectionRequestName: String?,
    val address: String?,
    val creator: String?,
    val createdAt: String,
    val foremanReviewedAt: String?,
    val materialsChangedAt: String?,
    val materialsCorrectedAt: String?,
    val canForemanReview: Boolean,
    val canEditMaterials: Boolean,
    val canAcknowledge: Boolean,
    val cachedAt: Long = System.currentTimeMillis(),
)

fun ActListItem.toEntity(): ActEntity = ActEntity(
    id                     = id,
    number                 = number,
    type                   = type,
    status                 = status,
    ticketId               = ticket_id,
    ticketNumber           = ticket_number,
    connectionRequestId    = connection_request_id,
    connectionRequestName  = connection_request_name,
    address                = address,
    creator                = creator,
    createdAt              = created_at,
    foremanReviewedAt      = foreman_reviewed_at,
    materialsChangedAt     = materials_changed_at,
    materialsCorrectedAt   = materials_corrected_at,
    canForemanReview       = can.foreman_review,
    canEditMaterials       = can.edit_materials,
    canAcknowledge         = can.acknowledge,
)
