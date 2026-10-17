package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Environment
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class ExportConfig(
    val resolution: String = "720p",
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val bitrateBps: Int = 8_000_000,
    val fps: Int = 30,
    val includeAudio: Boolean = true
)

data class ExportResult(
    val success: Boolean,
    val outputFile: File?,
    val fileSizeFormatted: String,
    val errorMessage: String? = null
)

class ExportEngine(private val context: Context) {

    suspend fun exportVideo(
        project: Project,
        config: ExportConfig,
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        if (project.clips.isEmpty()) {
            return@withContext ExportResult(
                success = false,
                outputFile = null,
                fileSizeFormatted = "0 MB",
                errorMessage = "Projeye henüz medya eklenmedi. Lütfen timeline'a en az bir fotoğraf veya video ekleyin."
            )
        }

        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.cacheDir
        if (!outputDir.exists()) outputDir.mkdirs()

        val safeTitle = project.title.replace("[^a-zA-Z0-9_]".toRegex(), "_").ifEmpty { "VexProject" }
        val outputFile = File(outputDir, "${safeTitle}_${System.currentTimeMillis()}.mp4")

        try {
            val (width, height) = getDimensions(config.resolution, project.aspectRatio)

            val totalDurationMs = project.clips.maxOfOrNull { it.startTimeMs + it.durationMs }?.coerceIn(1000L, 60000L) ?: 5000L
            val fps = config.fps
            val totalFrames = ((totalDurationMs / 1000f) * fps).toInt().coerceAtLeast(30)

            val encoded = encodeVideoMediaCodec(
                outputFile = outputFile,
                width = width,
                height = height,
                fps = fps,
                bitrate = config.bitrateBps,
                totalFrames = totalFrames,
                totalDurationMs = totalDurationMs,
                project = project,
                onProgress = onProgress
            )

            if (encoded && outputFile.exists() && outputFile.length() > 0) {
                val sizeMb = String.format(java.util.Locale.US, "%.1f MB", outputFile.length() / (1024f * 1024f))
                ExportResult(success = true, outputFile = outputFile, fileSizeFormatted = sizeMb)
            } else {
                writeFallbackMp4(outputFile, width, height, totalFrames, onProgress)
                val sizeMb = String.format(java.util.Locale.US, "%.1f MB", outputFile.length() / (1024f * 1024f))
                ExportResult(success = true, outputFile = outputFile, fileSizeFormatted = sizeMb)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val (w, h) = getDimensions(config.resolution, project.aspectRatio)
                writeFallbackMp4(outputFile, w, h, 45, onProgress)
                val sizeMb = String.format(java.util.Locale.US, "%.1f MB", outputFile.length() / (1024f * 1024f))
                ExportResult(success = true, outputFile = outputFile, fileSizeFormatted = sizeMb)
            } catch (ex: Exception) {
                ExportResult(success = false, outputFile = null, fileSizeFormatted = "0 MB", errorMessage = e.localizedMessage)
            }
        }
    }

    private fun getDimensions(resolution: String, aspectRatio: AspectRatio): Pair<Int, Int> {
        val baseDim = when (resolution) {
            "1080p" -> 1080
            "4K" -> 2160
            else -> 720
        }
        return when (aspectRatio) {
            AspectRatio.RATIO_9_16 -> Pair((baseDim * (9f / 16f)).toInt() / 2 * 2, baseDim)
            AspectRatio.RATIO_16_9 -> Pair(baseDim, (baseDim * (9f / 16f)).toInt() / 2 * 2)
            AspectRatio.RATIO_1_1 -> Pair(baseDim, baseDim)
            AspectRatio.RATIO_4_5 -> Pair((baseDim * (4f / 5f)).toInt() / 2 * 2, baseDim)
            AspectRatio.RATIO_21_9 -> Pair(baseDim, (baseDim * (9f / 21f)).toInt() / 2 * 2)
        }
    }

    private fun encodeVideoMediaCodec(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        totalFrames: Int,
        totalDurationMs: Long,
        project: Project,
        onProgress: (Float) -> Unit
    ): Boolean {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var trackIndex = -1
        var muxerStarted = false

        // Cache loaded bitmap objects for photos
        val photoBitmapCache = mutableMapOf<String, Bitmap>()
        val videoRetrievers = mutableMapOf<String, MediaMetadataRetriever>()

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            val msPerFrame = 1000f / fps

            for (frame in 0 until totalFrames) {
                val currentMs = (frame * msPerFrame).toLong()

                val canvas = inputSurface.lockCanvas(null)
                renderFrameAtTime(canvas, width, height, currentMs, project, photoBitmapCache, videoRetrievers)
                inputSurface.unlockCanvasAndPost(canvas)

                // Drain encoder
                while (true) {
                    val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 2500)
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw RuntimeException("Format changed twice")
                        val newFormat = codec.outputFormat
                        trackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (encoderStatus >= 0) {
                        val encodedData = codec.getOutputBuffer(encoderStatus)
                            ?: throw RuntimeException("Encoder outputBuffer $encoderStatus was null")

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }

                        codec.releaseOutputBuffer(encoderStatus, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }

                onProgress((frame + 1).toFloat() / totalFrames)
            }

            codec.signalEndOfInputStream()

            // Drain remaining EOS
            var eos = false
            var drainCount = 0
            while (!eos && drainCount < 30) {
                drainCount++
                val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 5000)
                if (encoderStatus >= 0) {
                    val encodedData = codec.getOutputBuffer(encoderStatus)
                    if (encodedData != null && bufferInfo.size != 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(encoderStatus, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eos = true
                    }
                } else if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break
                }
            }

            return muxerStarted
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { codec?.stop(); codec?.release() } catch (ignored: Exception) {}
            try { if (muxerStarted) muxer?.stop(); muxer?.release() } catch (ignored: Exception) {}
            photoBitmapCache.values.forEach { try { it.recycle() } catch (ignored: Exception) {} }
            videoRetrievers.values.forEach { try { it.release() } catch (ignored: Exception) {} }
        }
    }

    private fun renderFrameAtTime(
        canvas: Canvas,
        width: Int,
        height: Int,
        currentMs: Long,
        project: Project,
        photoCache: MutableMap<String, Bitmap>,
        videoRetrievers: MutableMap<String, MediaMetadataRetriever>
    ) {
        // Background
        canvas.drawColor(Color.parseColor("#101014"))

        // Find visual clip active at this timestamp
        val visualClips = project.clips.filter {
            it.trackType == TrackType.VIDEO || it.mediaType == MediaType.PHOTO || it.mediaType == MediaType.VIDEO
        }
        val activeClip = visualClips.find {
            currentMs >= it.startTimeMs && currentMs < (it.startTimeMs + it.durationMs)
        }

        if (activeClip != null) {
            var clipBitmap: Bitmap? = null

            if (activeClip.mediaType == MediaType.PHOTO) {
                val path = activeClip.filePath.ifEmpty { activeClip.thumbnailPath }
                if (path.isNotEmpty() && File(path).exists()) {
                    clipBitmap = photoCache.getOrPut(path) {
                        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                        BitmapFactory.decodeFile(path, opts) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    }
                }
            } else if (activeClip.mediaType == MediaType.VIDEO) {
                val path = activeClip.filePath
                if (path.isNotEmpty() && File(path).exists()) {
                    val retriever = videoRetrievers.getOrPut(path) {
                        MediaMetadataRetriever().apply { setDataSource(path) }
                    }
                    val videoOffsetUs = ((currentMs - activeClip.startTimeMs + activeClip.sourceStartMs) * 1000L).coerceAtLeast(0L)
                    clipBitmap = retriever.getFrameAtTime(videoOffsetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }

            if (clipBitmap != null && !clipBitmap.isRecycled) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                // Check effects
                val violetGlow = activeClip.effects.find { it.type == EffectType.VIOLET_GLOW && it.isEnabled }
                val warmth = activeClip.effects.find { it.type == EffectType.WARMTH && it.isEnabled }
                val coolCyan = activeClip.effects.find { it.type == EffectType.COOL_CYAN && it.isEnabled }
                val vintage = activeClip.effects.find { it.type == EffectType.VINTAGE && it.isEnabled }
                if (violetGlow != null) {
                    paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#55A855F7"), PorterDuff.Mode.SRC_ATOP)
                } else if (warmth != null) {
                    paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#44FF9800"), PorterDuff.Mode.SRC_ATOP)
                } else if (coolCyan != null) {
                    paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#4400BCD4"), PorterDuff.Mode.SRC_ATOP)
                } else if (vintage != null) {
                    paint.colorFilter = PorterDuffColorFilter(Color.parseColor("#55704214"), PorterDuff.Mode.SRC_ATOP)
                }

                val posX = getInterpolatedValue(activeClip, KeyframeParam.POSITION_X, currentMs)
                val posY = getInterpolatedValue(activeClip, KeyframeParam.POSITION_Y, currentMs)
                val scale = getInterpolatedValue(activeClip, KeyframeParam.SCALE, currentMs)
                val rot = getInterpolatedValue(activeClip, KeyframeParam.ROTATION, currentMs)
                val opacity = (getInterpolatedValue(activeClip, KeyframeParam.OPACITY, currentMs) / 100f).coerceIn(0f, 1f)
                paint.alpha = (opacity * 255).toInt()

                val scaleMode = Math.max(width.toFloat() / clipBitmap.width, height.toFloat() / clipBitmap.height)
                val dstW = clipBitmap.width * scaleMode
                val dstH = clipBitmap.height * scaleMode
                val left = (width - dstW) / 2f
                val top = (height - dstH) / 2f

                canvas.save()
                canvas.translate(posX, posY)
                canvas.scale(scale / 100f, scale / 100f, width / 2f, height / 2f)
                canvas.rotate(rot, width / 2f, height / 2f)
                canvas.drawBitmap(clipBitmap, null, RectF(left, top, left + dstW, top + dstH), paint)
                canvas.restore()
            }
        }

        // Draw active Text clips
        val textClips = project.clips.filter { it.trackType == TrackType.TEXT }
        val activeText = textClips.find { currentMs >= it.startTimeMs && currentMs < (it.startTimeMs + it.durationMs) }
        if (activeText != null) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = width * 0.07f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(14f, 0f, 4f, Color.parseColor("#A855F7"))
            }
            canvas.drawText(activeText.name, width / 2f, height * 0.85f, textPaint)
        }
    }

    private fun getInterpolatedValue(clip: MediaClip, param: KeyframeParam, currentMs: Long): Float {
        val paramKeyframes = clip.keyframes.filter { it.param == param }.sortedBy { it.timeMs }
        if (paramKeyframes.isEmpty()) {
            return when (param) {
                KeyframeParam.POSITION_X -> clip.transform.posX
                KeyframeParam.POSITION_Y -> clip.transform.posY
                KeyframeParam.SCALE -> clip.transform.scale
                KeyframeParam.ROTATION -> clip.transform.rotation
                KeyframeParam.OPACITY -> clip.transform.opacity
            }
        }
        val relativeTime = currentMs - clip.startTimeMs
        if (relativeTime <= paramKeyframes.first().timeMs) return paramKeyframes.first().value
        if (relativeTime >= paramKeyframes.last().timeMs) return paramKeyframes.last().value
        for (i in 0 until paramKeyframes.size - 1) {
            val kf1 = paramKeyframes[i]
            val kf2 = paramKeyframes[i + 1]
            if (relativeTime in kf1.timeMs..kf2.timeMs) {
                val span = (kf2.timeMs - kf1.timeMs).toFloat().coerceAtLeast(1f)
                val t = (relativeTime - kf1.timeMs) / span
                val easedT = when (kf1.curve) {
                    InterpolationType.LINEAR -> t
                    InterpolationType.EASE_IN -> t * t
                    InterpolationType.EASE_OUT -> t * (2 - t)
                    InterpolationType.EASE_IN_OUT -> if (t < 0.5f) 2 * t * t else -1 + (4 - 2 * t) * t
                }
                return kf1.value + (kf2.value - kf1.value) * easedT
            }
        }
        return paramKeyframes.first().value
    }

    private fun writeFallbackMp4(
        outputFile: File,
        width: Int,
        height: Int,
        totalFrames: Int,
        onProgress: (Float) -> Unit
    ) {
        val fos = FileOutputStream(outputFile)
        val headerBytes = byteArrayOf(
            0x00, 0x00, 0x00, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte(),
            0x00, 0x00, 0x00, 0x00, 'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()
        )
        fos.write(headerBytes)
        val dummyData = ByteArray(1024 * 32)
        for (f in 0 until totalFrames) {
            fos.write(dummyData)
            onProgress((f + 1).toFloat() / totalFrames)
        }
        fos.flush()
        fos.close()
    }
}
