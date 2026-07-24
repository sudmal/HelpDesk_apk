package com.sputnik.helpdesk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey val id: Int,
    val code: String,
    val name: String,
    val unit: String,
    val price: Double,
)
