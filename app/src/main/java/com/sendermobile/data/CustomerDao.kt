package com.sendermobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE isEgyptian = :egyptian ORDER BY name COLLATE NOCASE")
    fun observeByEgyptianStatus(egyptian: Boolean): Flow<List<Customer>>

    @Query("SELECT COUNT(*) FROM customers")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(customers: List<Customer>): List<Long>

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}
