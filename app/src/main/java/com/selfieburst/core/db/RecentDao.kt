package com.selfieburst.core.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.selfieburst.core.db.model.RecentPic

/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
@Dao
abstract class RecentDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(recent: RecentPic)


    @Transaction
    open suspend fun putRecent(recent: RecentPic) {
        insert(recent)
        if (count() > 3) {
            removeOldest()
        }
    }


    @Query("SELECT COUNT(path) FROM recent_pic")
    abstract suspend fun count(): Int


    @Query("DELETE FROM recent_pic WHERE time = (SELECT MIN(time) FROM recent_pic)")
    abstract suspend fun removeOldest()


    @Query("SELECT * FROM recent_pic ORDER BY time DESC LIMIT 3")
    abstract fun select(): LiveData<List<RecentPic>>


}