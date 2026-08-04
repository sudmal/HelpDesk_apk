package com.sputnik.helpdesk.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActDao {

    @Query("SELECT * FROM acts ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<ActEntity>>

    @Query("SELECT COUNT(*) FROM acts WHERE status = 'pending_foreman' AND canForemanReview = 1")
    fun pendingForemanCountFlow(): Flow<Int>

    @Query("SELECT * FROM acts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ActEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ActEntity)

    @Query("DELETE FROM acts WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM acts")
    suspend fun deleteAll()
}
