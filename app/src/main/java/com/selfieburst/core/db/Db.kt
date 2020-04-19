package com.selfieburst.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.selfieburst.core.db.model.RecentPic

/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
@Database(entities = [RecentPic::class], version = 1, exportSchema = false)
abstract class Db: RoomDatabase() {

    abstract fun recentDao(): RecentDao

}