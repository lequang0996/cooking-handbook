package com.fws.cookinghandbook.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fws.cookinghandbook.CookApp
import java.util.concurrent.TimeUnit

/** Background check for new/changed dishes (and their Gemini translation), once a day, only with a network connection. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CookApp
        app.repository.load()
        val r = app.repository.sync()
        if (!r.ok) return Result.retry()
        // new/changed dishes are translated with Gemini (needs at least one API key; otherwise they stay queued)
        app.translations.markChanged(r.changedPaths)
        val translated = app.translations.translatePendingAndWait(app.repository.dishes.value)
        return if (translated) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("dish-sync", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
