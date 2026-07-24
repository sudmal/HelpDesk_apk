package com.sputnik.helpdesk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_comments")
data class PendingCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticketId: Int,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
)
