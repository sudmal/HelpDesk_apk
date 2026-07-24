package com.sputnik.helpdesk.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionRequestDao {

    @Query("SELECT * FROM connection_requests ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<ConnectionRequestEntity>>

    @Query("SELECT id FROM connection_requests")
    suspend fun getAllIds(): List<Int>

    @Query("SELECT * FROM connection_requests WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ConnectionRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ConnectionRequestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ConnectionRequestEntity)

    @Query("DELETE FROM connection_requests")
    suspend fun deleteAll()
}
