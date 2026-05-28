package com.example

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityTest {

    @Test
    fun testMainActivity() {
        try {
            val activity = Robolectric.buildActivity(MainActivity::class.java).create().resume().get()
            println("Activity created successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
