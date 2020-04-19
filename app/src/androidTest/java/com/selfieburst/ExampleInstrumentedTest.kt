package com.selfieburst

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.selfieburst.app.camera.LiveTimer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private lateinit var ctx: Context

    @Before
    fun setup() {
        ctx = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.selfieburst", appContext.packageName)
    }

    @Test
    fun liveTimerCountTest() {

        var count = 5

        val period = 1

        val liveTimer = LiveTimer(count, period)

        val observer = Observer<LiveTimer> {
            count--
            println("time $count")
            if (it.isFinished) assertEquals(count, 0)
        }

        Handler(Looper.getMainLooper()).post {
            liveTimer.observeForever(observer)
        }

        // Make test thread to wait until we get all ticks from timer
        Thread.sleep((count + 1) * 1000L)
    }


    @Test
    fun liveTimerSuspendTest(){

    }


    @Test
    fun userManagerPersistenceTest(){

    }

}
