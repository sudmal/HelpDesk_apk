package com.sputnik.helpdesk.data.db

import androidx.room.*

@Dao
interface MaterialDao {

    @Query("SELECT * FROM materials ORDER BY name ASC")
    suspend fun getAll(): List<MaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(materials: List<MaterialEntity>)

    @Query("DELETE FROM materials")
    suspend fun deleteAll()
}
