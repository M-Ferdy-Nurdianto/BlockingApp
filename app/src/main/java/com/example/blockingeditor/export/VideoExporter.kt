package com.example.blockingeditor.export

import android.content.Context
import android.graphics.Paint
import android.util.Log
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.blockingeditor.animation.AnimationEngine
import com.example.blockingeditor.model.Project
import java.io.File

object VideoExporter {
    private const val MIME_TYPE = "video/avc"
    private const val FRAME_RATE = 30
    private const val IFRAME_INTERVAL = 5
    private const val BIT_RATE = 8000000 // 8 Mbps for sharp 1080p

    fun exportProjectToVideo(context: Context, project: Project, onProgress: (Float) -> Unit, onComplete: (File) -> Unit) {
        val width = 1080
        val height = 1920
        val outputFile = File(context.cacheDir, "dance_blocking.mp4")
        
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        }

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        var exportSuccessful = false

        val paint = Paint()
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            val totalDurationMs = project.formations.lastOrNull()?.timeMs ?: 0L
            // Total frames = transition frames + 1 for the final snapshot
            val totalFrames = ((totalDurationMs / 1000f) * FRAME_RATE).toLong().coerceAtLeast(1) + 1
            var currentFrameCount = 0L

            // Timing Sync: unlockCanvasAndPost uses system time. 
            // We must slow down the loop to match FRAME_RATE exactly.
            val baseTimeNs = System.nanoTime()
            val frameDurationNs = 1_000_000_000L / FRAME_RATE

            for (i in 0 until project.formations.size - 1) {
                val startFormation = project.formations[i]
                val endFormation = project.formations[i + 1]
                val durationMs = endFormation.timeMs - startFormation.timeMs
                val framesInTransition = (durationMs / 1000f * FRAME_RATE).toInt().coerceAtLeast(1)

                for (f in 0 until framesInTransition) {
                    val t = f.toFloat() / framesInTransition
                    val animatedDancers = AnimationEngine.interpolateDancers(startFormation.dancers, endFormation.dancers, t)
                    
                    // Throttle to real-time to ensure Canvas timestamps are correct
                    val targetTimeNs = baseTimeNs + (currentFrameCount * frameDurationNs)
                    while (System.nanoTime() < targetTimeNs) {
                        Thread.sleep(1)
                    }

                    val canvas = inputSurface.lockCanvas(null)
                    try {
                        drawFrame(canvas, project, animatedDancers, width, height, paint)
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    drainEncoder(codec, bufferInfo, muxer, muxerStarted, trackIndex) { newIndex, started ->
                        trackIndex = newIndex
                        muxerStarted = started
                    }

                    currentFrameCount++
                    onProgress((currentFrameCount.toFloat() / totalFrames).coerceIn(0f, 1f))
                }
            }

            val lastFormation = project.formations.lastOrNull()
            if (lastFormation != null) {
                 // Final throttle
                 val targetTimeNs = baseTimeNs + (currentFrameCount * frameDurationNs)
                 while (System.nanoTime() < targetTimeNs) { Thread.sleep(1) }

                 val canvas = inputSurface.lockCanvas(null)
                 try {
                     drawFrame(canvas, project, lastFormation.dancers, width, height, paint)
                 } finally {
                     inputSurface.unlockCanvasAndPost(canvas)
                 }
                 drainEncoder(codec, bufferInfo, muxer, muxerStarted, trackIndex) { newIndex, started ->
                    trackIndex = newIndex
                    muxerStarted = started
                 }
                 currentFrameCount++
                 onProgress(1.0f)
            }

            codec.signalEndOfInputStream()
            // Final drain to catch the EOS flag
            drainEncoder(codec, bufferInfo, muxer, muxerStarted, trackIndex) { newIndex, started ->
                trackIndex = newIndex
                muxerStarted = started
            }
            exportSuccessful = true

        } catch (e: Exception) {
            Log.e("VideoExporter", "Export failed", e)
        } finally {
            try { 
                codec.stop() 
                codec.release()
            } catch (e: Exception) {
                Log.e("VideoExporter", "Error releasing codec", e)
            }
            
            try {
                if (muxerStarted) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (e: Exception) {
                Log.e("VideoExporter", "Error releasing muxer", e)
            }
            
            inputSurface.release()
            
            // Call onComplete ONLY after resources are safely closed
            if (exportSuccessful) {
                onComplete(outputFile)
            }
        }
    }

    private fun drawFrame(canvas: android.graphics.Canvas, project: Project, dancers: List<com.example.blockingeditor.model.Dancer>, width: Int, height: Int, paint: Paint) {
        canvas.drawColor(android.graphics.Color.BLACK)
        
        // Fix scaling with zero-safety
        val scaleX = width.toFloat() / project.stageWidth.coerceAtLeast(1f)
        val scaleY = height.toFloat() / project.stageHeight.coerceAtLeast(1f)

        // Draw GRID
        paint.color = android.graphics.Color.DKGRAY
        paint.strokeWidth = 1f
        for (x in 0..width step 100) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        for (y in 0..height step 100) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)

        // Draw DANCERS
        dancers.forEach { dancer ->
            val drawX = dancer.x * scaleX
            val drawY = dancer.y * scaleY
            
            paint.color = dancer.color.toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(drawX, drawY, 45f, paint)
            
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(drawX, drawY, 45f, paint)
            
            paint.style = Paint.Style.FILL
            paint.textSize = 35f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(dancer.name, drawX, drawY + 80f, paint)
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        muxerStarted: Boolean,
        trackIndex: Int,
        onMuxerChanged: (Int, Boolean) -> Unit
    ) {
        var currentTrackIndex = trackIndex
        var currentMuxerStarted = muxerStarted

        while (true) {
            val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 50000) // Increased timeout to 50ms
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (currentMuxerStarted) break 
                val newFormat = codec.outputFormat
                currentTrackIndex = muxer.addTrack(newFormat)
                muxer.start()
                currentMuxerStarted = true
                onMuxerChanged(currentTrackIndex, currentMuxerStarted)
            } else if (encoderStatus < 0) {
                // Ignore
            } else {
                val encodedData = codec.getOutputBuffer(encoderStatus) ?: throw RuntimeException("buffer was null")
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    if (!currentMuxerStarted) throw RuntimeException("muxer not started")
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(currentTrackIndex, encodedData, bufferInfo)
                }

                codec.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }
}
