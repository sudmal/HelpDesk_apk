package com.sputnik.helpdesk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingCommentDao {

    @Insert
    suspend fun insert(comment: PendingCommentEntity)

    @Query("SELECT * FROM pending_comments ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingCommentEntity>

    @Query("DELETE FROM pending_comments WHERE id = :id")
    suspend fun deleteById(id: Int)
}
