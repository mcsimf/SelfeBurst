package com.selfieburst.core

import android.content.Context
import androidx.room.Room
import com.selfieburst.core.api.Api
import com.selfieburst.core.db.Db

/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
class Core(context: Context) {


    private val api: Api = Api()


    val userManager: UserManager = UserManager(context, api)


    val db: Db = Room.databaseBuilder(context, Db::class.java, "db")
        .fallbackToDestructiveMigration()
        .build()


    companion object {
        @Volatile
        private var INSTANCE: Core? = null

        fun get(context: Context): Core =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context).also { INSTANCE = it }
            }

        private fun create(context: Context): Core = Core(context)

    }

}