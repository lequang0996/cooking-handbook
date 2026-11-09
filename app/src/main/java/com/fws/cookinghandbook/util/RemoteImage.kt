package com.fws.cookinghandbook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Tiny image loader (no extra library, keeps the APK small): downloads a photo once on demand,
 * keeps it in the app cache folder (the OS may clear it) and decodes it down-sized.
 */
object ImageLoader {
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun name(url: String) =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun load(context: Context, url: String, maxWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        runCatching {
            val dir = File(context.cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, name(url))
            if (!file.exists()) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                try {
                    if (c.responseCode != 200 || c.contentLengthLong > 12_000_000) return@withContext null
                    val tmp = File(dir, file.name + ".tmp")
                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    tmp.renameTo(file)
                } finally { c.disconnect() }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { memory.put(url, it) }
    }
}

/** Shows a remote photo; takes no space if it cannot be loaded (offline etc.). */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val b = ImageLoader.load(context, url, 1080)
        if (b == null) failed = true else bitmap = b
    }
    if (failed) return
    val b = bitmap
    if (b == null) Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    else Image(b.asImageBitmap(), null, modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
}
