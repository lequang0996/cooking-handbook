package com.fws.cookinghandbook

import android.app.Application
import com.fws.cookinghandbook.data.DishRepository
import com.fws.cookinghandbook.data.Prefs
import com.fws.cookinghandbook.data.SyncWorker

class CookApp : Application() {
    val prefs by lazy { Prefs(this) }
    val repository by lazy { DishRepository(this, prefs) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.schedule(this)
    }
}
