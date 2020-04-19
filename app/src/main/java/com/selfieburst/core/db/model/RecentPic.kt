package com.selfieburst.core.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
@Entity(tableName = "recent_pic")
data class RecentPic(
    val path: String,
    @PrimaryKey
    val time: Long
)