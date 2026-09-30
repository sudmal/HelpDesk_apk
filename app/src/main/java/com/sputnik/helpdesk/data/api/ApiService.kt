package com.sputnik.helpdesk.data.api

import com.sputnik.helpdesk.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("tickets")
    suspend fun getTickets(): TicketsResponse

    @GET("tickets/{id}")
    suspend fun getTicket(@Path("id") ticketId: Int): Ticket

    @Multipart
    @POST("tickets/{id}/comments")
    suspend fun addComment(
        @Path("id") ticketId: Int,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part attachments: List<MultipartBody.Part>,
    ): AddCommentResponse

    @Multipart
    @POST("tickets/{id}/close")
    suspend fun closeTicket(
        @Path("id") ticketId: Int,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part attachments: List<MultipartBody.Part>,
    ): Ticket

    @POST("tickets/{id}/reschedule")
    suspend fun rescheduleTicket(
        @Path("id") ticketId: Int,
        @Body request: RescheduleRequest,
    ): Ticket

    @POST("tickets/{id}/cancel")
    suspend fun cancelTicket(
        @Path("id") ticketId: Int,
        @Body request: CancelRequest,
    ): Ticket

    @POST("tickets/{id}/reopen")
    suspend fun reopenTicket(@Path("id") ticketId: Int): Ticket

    @Multipart
    @POST("tickets/{id}/attachments")
    suspend fun uploadAttachments(
        @Path("id") ticketId: Int,
        @Part attachments: List<MultipartBody.Part>,
    ): AttachmentsResponse

    @GET("service_types")
    suspend fun getServiceTypes(): List<ServiceType>

    @GET("materials")
    suspend fun getMaterials(): List<Material>

    @GET("promotions")
    suspend fun getPromotions(): List<Promotion>

    // --- Connection requests ---

    @GET("connection-requests")
    suspend fun getConnectionRequests(
        @Query("territory_id") territoryId: Int? = null,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
    ): ConnectionRequestsResponse

    @POST("connection-requests")
    suspend fun createConnectionRequest(@Body request: CreateConnectionReq): ConnectionRequest

    @GET("connection-requests/{id}")
    suspend fun getConnectionRequest(@Path("id") id: Int): ConnectionRequest

    @PUT("connection-requests/{id}")
    suspend fun updateConnectionRequest(
        @Path("id") id: Int,
        @Body request: UpdateConnectionReq,
    ): ConnectionRequest

    @POST("connection-requests/{id}/close")
    suspend fun closeConnectionRequest(
        @Path("id") id: Int,
        @Body request: CloseConnectionReq,
    ): ConnectionRequest

    @POST("connection-requests/{id}/feasibility")
    suspend fun answerFeasibility(
        @Path("id") id: Int,
        @Body request: FeasibilityReq,
    ): ConnectionRequest

    @POST("connection-requests/{id}/cancel")
    suspend fun cancelConnectionRequest(
        @Path("id") id: Int,
        @Body request: CancelRequest,
    ): ConnectionRequest

    @DELETE("connection-requests/{id}")
    suspend fun deleteConnectionRequest(@Path("id") id: Int)

    @GET("schedule")
    suspend fun getSchedule(
        @Query("months[]") months: List<String>? = null,
    ): ScheduleResponse

    // --- Acts ---

    @GET("acts")
    suspend fun getActs(): ActsResponse

    @GET("acts/{id}")
    suspend fun getAct(@Path("id") actId: Int): Act

    @POST("acts/{id}/approve")
    suspend fun approveAct(@Path("id") actId: Int): Act

    @POST("acts/{id}/materials")
    suspend fun addActMaterial(
        @Path("id") actId: Int,
        @Body request: MaterialItem,
    ): Act

    @PUT("acts/{id}/materials/{materialId}")
    suspend fun updateActMaterial(
        @Path("id") actId: Int,
        @Path("materialId") materialId: Int,
        @Body request: UpdateActMaterialReq,
    ): Act

    @DELETE("acts/{id}/materials/{materialId}")
    suspend fun deleteActMaterial(
        @Path("id") actId: Int,
        @Path("materialId") materialId: Int,
    ): Act

    @POST("acts/{id}/acknowledge")
    suspend fun acknowledgeAct(@Path("id") actId: Int): Act

    @PUT("acts/{id}/promotion")
    suspend fun updateActPromotion(
        @Path("id") actId: Int,
        @Body request: UpdateActPromotionReq,
    ): Act

    // --- Profile ---

    @GET("profile")
    suspend fun getProfile(): Profile

    @PUT("profile")
    suspend fun updateProfile(@Body request: UpdateProfileReq): Profile
}
