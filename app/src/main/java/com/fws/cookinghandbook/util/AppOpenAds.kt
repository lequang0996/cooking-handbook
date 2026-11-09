package com.fws.cookinghandbook.util

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.lang.ref.WeakReference

/**
 * App-open ad: shown when the app is opened (once the ad SDK is ready, within a few seconds), and again when the
 * user returns after more than 30 minutes away. Never more than once every 4 minutes, never an ad older than 4 h.
 */
class AppOpenAdManager(private val unitId: String) {
    private var ad: AppOpenAd? = null
    private var loading = false
    private var loadedAt = 0L
    private var showing = false
    private var lastShownAt = 0L
    private var lastStopAt = 0L
    private var showDeadline = now() + WAIT_MS
    private var enabled = false
    private var activityRef: WeakReference<Activity>? = null

    private fun now() = System.currentTimeMillis()
    private fun fresh() = ad != null && now() - loadedAt < 4 * HOUR

    /** Call when consent allows ads and the SDK is initialised. */
    fun enable(activity: Activity) { enabled = true; activityRef = WeakReference(activity); load(activity); tryShow(activity) }

    fun onStart(activity: Activity) {
        activityRef = WeakReference(activity)
        if (lastStopAt != 0L && now() - lastStopAt > 30 * 60_000L) showDeadline = now() + WAIT_MS
        if (enabled) { load(activity); tryShow(activity) }
    }

    fun onStop() { lastStopAt = now() }

    private fun load(activity: Activity) {
        if (loading || fresh()) return
        loading = true
        AppOpenAd.load(activity, unitId, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(loaded: AppOpenAd) {
                ad = loaded; loadedAt = now(); loading = false
                activityRef?.get()?.let { tryShow(it) }
            }
            override fun onAdFailedToLoad(error: LoadAdError) { loading = false }
        })
    }

    private fun tryShow(activity: Activity) {
        val a = ad ?: return
        if (showing || !fresh() || now() > showDeadline || now() - lastShownAt < 4 * 60_000L) return
        val resumed = (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        if (!resumed) return
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { ad = null; showing = false; load(activity) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { ad = null; showing = false }
        }
        showDeadline = 0L; lastShownAt = now(); showing = true
        a.show(activity)
    }

    private companion object {
        const val WAIT_MS = 8_000L
        const val HOUR = 3_600_000L
    }
}
