package com.sputnik.helpdesk.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {

    @Query("SELECT * FROM tickets WHERE `group` = :group ORDER BY scheduledAt ASC")
    suspend fun getByGroup(group: String): List<TicketEntity>

    @Query("SELECT * FROM tickets WHERE `group` = :group AND (:stName IS NULL OR serviceTypeName = :stName) ORDER BY scheduledAt ASC")
    suspend fun getByGroupFiltered(group: String, stName: String?): List<TicketEntity>

    @Query("SELECT * FROM tickets WHERE `group` = :group AND (:stName IS NULL OR serviceTypeName = :stName) ORDER BY scheduledAt ASC")
    fun getByGroupFilteredFlow(group: String, stName: String?): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): TicketEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tickets: List<TicketEntity>)

    @Query("DELETE FROM tickets WHERE `group` = :group")
    suspend fun deleteByGroup(group: String)

    @Query("SELECT DISTINCT serviceTypeName FROM tickets WHERE serviceTypeName IS NOT NULL ORDER BY serviceTypeName ASC")
    suspend fun getDistinctServiceTypeNames(): List<String>

    @Query("DELETE FROM tickets WHERE cachedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
