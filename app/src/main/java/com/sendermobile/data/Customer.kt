package com.sendermobile.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers", indices = [Index(value = ["normalizedPhone"], unique = true)])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originalPhone: String,
    val normalizedPhone: String,
    val countryCode: String,
    val countryName: String,
    val isEgyptian: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
