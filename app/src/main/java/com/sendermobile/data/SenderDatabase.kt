package com.sendermobile.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Customer::class], version = 1, exportSchema = false)
abstract class SenderDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
}
