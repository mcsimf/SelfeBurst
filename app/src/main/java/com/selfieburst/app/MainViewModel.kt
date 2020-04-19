package com.selfieburst.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.selfieburst.app.camera.LiveTimer
import com.selfieburst.core.Core
import com.selfieburst.core.UserManager
import com.selfieburst.core.db.model.RecentPic

/**
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    /* User Auth */

    fun authState(): LiveData<UserManager.AuthState> {
        return Core.get(app).userManager
    }

    fun login(name: String, pass: String) {
        Core.get(app).userManager.logIn(name, pass)
    }

    fun logout() {
        Core.get(app).userManager.logOut()
    }

    /* Pictures */

    fun recentPics(): LiveData<List<RecentPic>> {
        return Core.get(app).db.recentDao().select()
    }

    suspend fun putRecent(recent: RecentPic) {
        Core.get(app).db.recentDao().putRecent(recent)
    }

    /* Timer */

    lateinit var timer: LiveTimer
        private set

    fun initTimer() {
        timer = LiveTimer(5, 1)
    }


}