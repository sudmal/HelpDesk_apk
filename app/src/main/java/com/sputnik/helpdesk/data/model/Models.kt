package com.sputnik.helpdesk.data.model

data class LoginRequest(val login: String, val password: String)

data class UpdateInfo(
    val version_code: Int,
    val version_name: String,
    val apk_url: String,
    val changelog: String?,
)

data class LoginResponse(val token: String, val user: UserInfo)

data class UserInfo(val id: Int, val name: String, val role: String)

data class TicketsResponse(
    val overdue: List<Ticket>,
    val today: List<Ticket>,
    val new_today: List<Ticket>,
    val tomorrow: List<Ticket>,
    val synced_at: String,
)

data class Ticket(
    val id: Int,
    val number: String,
    val created_at: String?,
    val scheduled_at: String?,
    val closed_at: String?,
    val description: String?,
    val phone: String?,
    val apartment: String?,
    val close_notes: String?,
    val closed_by: String?,
    val act_number: String?,
    val act: ActSummary? = null,
    val address: TicketAddress?,
    val territory: CRTerritory? = null,
    val type: String?,
    val type_allows_promotion: Boolean = false,
    val service_type: ServiceType?,
    val status: TicketStatus?,
    val brigade: String?,
    val assignee: String?,
    val comments: List<Comment>,
    val attachments: List<Attachment> = emptyList(),
    val can: TicketCan? = null,
)

/** Готовые серверные флаги доступности действий по заявке. Пока только reopen. */
data class TicketCan(
    val reopen: Boolean = false,
)

data class ServiceType(
    val id: Int,
    val name: String,
    val color: String?,
)

data class TicketAddress(
    val full: String,
    val street: String?,
    val building: String?,
)

data class TicketStatus(
    val name: String?,
    val is_final: Boolean,
    val color: String?,
    val slug: String?,
    val sort_order: Int = 0,
)

data class Comment(
    val id: Int,
    val body: String,
    val author: String?,
    val created_at: String,
    val attachments: List<Attachment> = emptyList(),
)

data class AddCommentResponse(
    val id: Int,
    val body: String,
    val author: String?,
    val created_at: String,
    val attachments: List<Attachment> = emptyList(),
)

data class Material(
    val id: Int,
    val code: String,
    val name: String,
    val unit: String,
    val price: Double,
)

data class Promotion(
    val id: Int,
    val name: String,
    val price: Double,
)

data class MaterialItem(val material_id: Int, val quantity: Double)

data class CloseTicketRequest(
    val close_notes: String?,
    val act_type: String?,
    val materials: List<MaterialItem>?,
)

data class RescheduleRequest(
    val scheduled_at: String,
    val comment: String?,
)

data class CancelRequest(
    val comment: String?,
)

data class Attachment(
    val id: Int,
    val original_name: String,
    val url: String,
    val mime_type: String,
    val size: Long,
)

data class AttachmentsResponse(
    val attachments: List<Attachment>,
)

// --- Acts ---

/** Short act card embedded in Ticket/ConnectionRequest — fetch GET /acts/{id} for materials/history. */
data class ActSummary(
    val id: Int,
    val number: String,
    val type: String?,
    val status: String,
    val materials_changed_at: String?,
    val promotion_name: String? = null,
    val promotion_price: Double? = null,
)

data class ActMaterial(
    val id: Int,
    val material_id: Int,
    val name: String,
    val code: String,
    val unit: String,
    val price_at_time: Double,
    val quantity: Double,
    val total: Double,
)

data class ActHistoryEntry(
    val id: Int,
    val user: String?,
    val action: String,
    val field: String?,
    val old_value: String?,
    val new_value: String?,
    val acknowledged_at: String?,
    val created_at: String,
)

data class ActCan(
    val foreman_review: Boolean,
    val edit_materials: Boolean,
    val acknowledge: Boolean,
)

data class Act(
    val id: Int,
    val number: String,
    val type: String,
    val status: String,
    val ticket_id: Int?,
    val connection_request_id: Int?,
    val created_at: String,
    val creator: String?,
    val foreman_reviewed_at: String?,
    val foreman_reviewed_by: String?,
    val materials_changed_at: String?,
    val materials_corrected_at: String? = null,
    val promotion_id: Int? = null,
    val promotion_name: String? = null,
    val promotion_price: Double? = null,
    val promotion_eligible: Boolean = false,
    val materials: List<ActMaterial> = emptyList(),
    val history: List<ActHistoryEntry> = emptyList(),
    val can: ActCan,
)

data class UpdateActMaterialReq(val quantity: Double)

data class UpdateActPromotionReq(val promotion_id: Int?)

/** Lightweight row for the "Акты" list screen — GET /acts. Full details via GET /acts/{id}. */
data class ActListItem(
    val id: Int,
    val number: String,
    val type: String,
    val status: String,
    val ticket_id: Int?,
    val ticket_number: String?,
    val connection_request_id: Int?,
    val connection_request_name: String?,
    val address: String?,
    val creator: String?,
    val created_at: String,
    val foreman_reviewed_at: String?,
    val materials_changed_at: String?,
    val materials_corrected_at: String? = null,
    val can: ActCan,
)

data class ActsResponse(
    val data: List<ActListItem>,
    val synced_at: String?,
)

// --- Profile ---

data class Profile(
    val id: Int,
    val name: String,
    val phone: String?,
    val email: String?,
    val telegram_chat_id: String?,
    val max_chat_id: String?,
    val notify_on_days_off: Boolean,
    val role: String,
)

data class UpdateProfileReq(
    val name: String,
    val phone: String?,
    val email: String?,
    val telegram_chat_id: String?,
    val max_chat_id: String?,
    val notify_on_days_off: Boolean,
)
