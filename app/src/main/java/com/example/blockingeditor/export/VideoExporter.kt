package com.example.blockingeditor.export

import android.content.Context
import android.graphics.Paint
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
    private const val BIT_RATE = 2000000

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

        val paint = Paint()
        val bufferInfo = MediaCodec.BufferInfo()

        // Total duration is the timestamp of the last formation
        val totalDurationMs = project.formations.lastOrNull()?.timeMs ?: 0L
        val totalFrames = (totalDurationMs / 1000f * FRAME_RATE).toInt().coerceAtLeast(1)
        
        var currentFrameCount = 0L

        for (i in 0 until project.formations.size - 1) {
            val startFormation = project.formations[i]
            val endFormation = project.formations[i + 1]
            val durationMs = endFormation.timeMs - startFormation.timeMs
            val framesInTransition = (durationMs / 1000f * FRAME_RATE).toInt().coerceAtLeast(1)

            for (f in 0 until framesInTransition) {
                val t = f.toFloat() / framesInTransition
                val animatedDancers = AnimationEngine.interpolateDancers(startFormation.dancers, endFormation.dancers, t)
                
                val canvas = inputSurface.lockCanvas(null)
                try {
                    // Draw Frame
                    canvas.drawColor(android.graphics.Color.BLACK)
                    
                    // Grid
                    paint.color = android.graphics.Color.DKGRAY
                    paint.strokeWidth = 1f
                    for (x in 0..width step 100) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
                    for (y in 0..height step 100) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)

                    // Dancers
                    animatedDancers.forEach { dancer ->
                        paint.color = dancer.color.toInt()
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(dancer.x, dancer.y, 45f, paint)
                        
                        paint.color = android.graphics.Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 4f
                        canvas.drawCircle(dancer.x, dancer.y, 45f, paint)
                        paint.style = Paint.Style.FILL

                        paint.textSize = 35f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(dancer.name, dancer.x, dancer.y + 80f, paint)
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain Encoder
                drainEncoder(codec, bufferInfo, muxer, muxerStarted, trackIndex) { newTrackIndex, started ->
                    trackIndex = newTrackIndex
                    muxerStarted = started
                }

                currentFrameCount++
                onProgress((currentFrameCount.toFloat() / totalFrames).coerceIn(0f, 1f))
            }
        }

        // Draw last frame state
        val lastFormation = project.formations.lastOrNull()
        if (lastFormation != null) {
             val canvas = inputSurface.lockCanvas(null)
             try {
                 canvas.drawColor(android.graphics.Color.BLACK)
                 lastFormation.dancers.forEach { dancer ->
                     paint.color = dancer.color.toInt()
                     paint.style = Paint.Style.FILL
                     canvas.drawCircle(dancer.x, dancer.y, 45f, paint)
                     paint.color = android.graphics.Color.WHITE
                     paint.style = Paint.Style.STROKE
                     canvas.drawCircle(dancer.x, dancer.y, 45f, paint)
                     paint.style = Paint.Style.FILL
                     paint.textSize = 35f
                     paint.textAlign = Paint.Align.CENTER
                     canvas.drawText(dancer.name, dancer.x, dancer.y + 80f, paint)
                 }
             } finally {
                 inputSurface.unlockCanvasAndPost(canvas)
             }
             drainEncoder(codec, bufferInfo, muxer, muxerStarted, trackIndex) { _, _ -> }
        }

        // Signal EOF
        codec.signalEndOfInputStream()
        drainEncoder(codec, bufferInfo, muxer, muxerStarted, trackIndex) { _, _ -> }

        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
        inputSurface.release()

        onComplete(outputFile)
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
            val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, 10000)
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
