package com.fws.cookinghandbook.util

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/** Google UMP consent first, then (and only if allowed) initialise AdMob. */
object AdsConsent {
    private val started = AtomicBoolean(false)

    fun init(activity: Activity, onReady: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (info.canRequestAds()) startAds(activity, onReady)
                }
            },
            { if (info.canRequestAds()) startAds(activity, onReady) },
        )
        // Consent gathered in an earlier session already allows ads.
        if (info.canRequestAds()) startAds(activity, onReady)
    }

    private fun startAds(activity: Activity, onReady: () -> Unit) {
        if (!started.compareAndSet(false, true)) return
        Thread {
            MobileAds.initialize(activity) { activity.runOnUiThread(onReady) }
        }.start()
    }

    fun privacyOptionsRequired(activity: Activity): Boolean =
        UserMessagingPlatform.getConsentInformation(activity).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }
}

@Composable
fun BannerAd(unitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val adView = remember(unitId, widthDp) {
        AdView(context).apply {
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adUnitId = unitId
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}
