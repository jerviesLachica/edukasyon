package com.edukasyon.studentai.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

private const val TAG = "Mp4VideoPlayer"

@Composable
fun Mp4VideoPlayer(
    modifier: Modifier = Modifier,
    @RawRes rawResId: Int? = null,
    filePath: String? = null,
    uri: Uri? = null,
    isLooping: Boolean = true,
    isMuted: Boolean = true,
) {
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(rawResId, filePath, uri) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.reset()
                mediaPlayer.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error disposing MediaPlayer", e)
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            try {
                                mediaPlayer.reset()
                                when {
                                    rawResId != null && rawResId != 0 -> {
                                        val afd = ctx.resources.openRawResourceFd(rawResId)
                                        mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                    }
                                    !filePath.isNullOrBlank() && File(filePath).exists() -> {
                                        mediaPlayer.setDataSource(filePath)
                                    }
                                    uri != null -> {
                                        mediaPlayer.setDataSource(ctx, uri)
                                    }
                                    else -> {
                                        Log.w(TAG, "No valid video source provided")
                                        return
                                    }
                                }

                                mediaPlayer.setSurface(Surface(surface))
                                mediaPlayer.isLooping = isLooping
                                if (isMuted) {
                                    mediaPlayer.setVolume(0f, 0f)
                                }
                                mediaPlayer.prepareAsync()
                                mediaPlayer.setOnPreparedListener { mp ->
                                    mp.start()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to initialize video playback", e)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            try {
                                mediaPlayer.setSurface(null)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error clearing surface", e)
                            }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            }
        )
    }
}
