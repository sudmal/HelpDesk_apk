package com.sputnik.helpdesk.data.model

data class ConnectionRequest(
    val id: Int,
    val name: String,
    val phone: String?,
    val address_string: String?,
    val description: String?,
    val status: String,
    val scheduled_at: String?,
    val notes: String?,
    val act_number: String?,
    val act: ActSummary? = null,
    val feasibility: String? = null,
    val feasibility_comment: String? = null,
    val feasibility_at: String? = null,
    val territory: CRTerritory?,
    val service_type: ServiceType? = null,
    val creator: String?,
    val created_at: String?,
    val updated_at: String?,
    val materials: List<ConnectionMaterialLine> = emptyList(),
)

data class CRTerritory(val id: Int, val name: String) : java.io.Serializable

data class ConnectionMaterialLine(
    val id: Int,
    val material_id: Int,
    val name: String,
    val code: String,
    val unit: String,
    val price_at_time: Double,
    val quantity: Double,
    val total: Double,
)

data class ConnectionRequestsResponse(
    val data: List<ConnectionRequest>,
    val current_page: Int,
    val last_page: Int,
    val total: Int,
    val territories: List<CRTerritory>,
    val synced_at: String?,
)

data class CreateConnectionReq(
    val name: String,
    val phone: String,
    val address_string: String,
    val territory_id: Int,
    val description: String? = null,
    val service_type_id: Int? = null,
)

data class UpdateConnectionReq(
    val name: String? = null,
    val phone: String? = null,
    val address_string: String? = null,
    val description: String? = null,
    val territory_id: Int? = null,
    val service_type_id: Int? = null,
    val status: String? = null,
    val scheduled_at: String? = null,
    val notes: String? = null,
)

data class CloseConnectionReq(
    val notes: String? = null,
    val materials: List<MaterialItem> = emptyList(),
    val promotion_id: Int? = null,
)

data class FeasibilityReq(
    val answer: String,
    val comment: String? = null,
)
