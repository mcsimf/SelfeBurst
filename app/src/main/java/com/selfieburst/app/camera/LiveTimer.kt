package com.selfieburst.app.camera

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData

/**
 * Lifecycle aware timer.
 * Since we go hard way, when config change take place and camera will be reinitialized
 * it may lead to event loss. I.e. timer counted to the point when picture should be taken
 * but camera at its destroy/create phases.
 * To avoid this LiveTimer will stop when there is no observers (camera destroyed),
 * and start when active observer appear (camera created).
 *
 * Sure, all that shit can be done by using Rx but who cares :)
 *
 * @author Maksym Fedyay on 4/18/20 (mcsimf@gmail.com).
 */
class LiveTimer(count: Int, period: Int) : LiveData<LiveTimer>() {


    var time: Int = 0
        private set


    var isFinished = false
        private set


    var used = false


    private val timerHandler = Handler(Looper.getMainLooper())


    private val timerTask = object : Runnable {
        override fun run() {
            if (hasActiveObservers()) {
                used = false
                ++time
                if (time == count) isFinished = true
                value = this@LiveTimer
            }
            if (time < count)
                timerHandler.postDelayed(this, period * 1000L)
        }
    }


    override fun onInactive() {
        timerHandler.removeCallbacksAndMessages(null)
    }


    override fun onActive() {
        timerHandler.postDelayed(timerTask, 1000)
    }

}