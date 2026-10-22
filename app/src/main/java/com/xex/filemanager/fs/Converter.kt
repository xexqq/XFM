@file:OptIn(UnstableApi::class)

package com.xex.filemanager.fs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.xex.filemanager.model.FileItem
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Converter {
    val imageTargets = listOf("jpg", "png", "webp", "pdf")
    val audioTargets = listOf("wav", "m4a")
    val videoTargets = listOf("mp4", "m4a")

    fun targetsFor(item: FileItem): List<String> = when (item.type) {
        com.xex.filemanager.model.FileType.IMAGE -> imageTargets
        com.xex.filemanager.model.FileType.AUDIO -> audioTargets
        com.xex.filemanager.model.FileType.VIDEO -> videoTargets
        else -> emptyList()
    }

    /** Загрузка картинки с учётом поворота из EXIF и уменьшением до [maxSide]. */
    fun loadBitmap(path: String, maxSide: Int): Bitmap {
        val b = Fs.backend(path)
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        b.input(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxSide > 0 && (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide)) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        val bmp = b.input(path).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Не удалось прочитать изображение")
        val orient = try {
            b.input(path).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun saveBitmap(bmp: Bitmap, destPath: String, fmt: String, quality: Int) {
        val out = Fs.backend(destPath).output(destPath, false)
        out.use { os ->
            when (fmt) {
                "png" -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
                "webp" -> {
                    @Suppress("DEPRECATION")
                    val f = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    bmp.compress(f, quality, os)
                }
                "pdf" -> {
                    val doc = PdfDocument()
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create())
                    page.canvas.drawBitmap(bmp, 0f, 0f, null)
                    doc.finishPage(page)
                    doc.writeTo(os)
                    doc.close()
                }
                else -> {
                    val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(flat)
                    c.drawColor(Color.WHITE)
                    c.drawBitmap(bmp, 0f, 0f, null)
                    flat.compress(Bitmap.CompressFormat.JPEG, quality, os)
                }
            }
        }
    }

    fun convertImage(src: FileItem, destPath: String, fmt: String, quality: Int) {
        val bmp = loadBitmap(src.path, 0)
        saveBitmap(bmp, destPath, fmt, quality)
    }

    /** Декодирует аудио в PCM и пишет WAV. */
    fun audioToWav(src: File, dest: File, p: Progress) {
        val ex = MediaExtractor()
        ex.setDataSource(src.path)
        var track = -1
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                track = i
                break
            }
        }
        if (track < 0) throw IOException("В файле нет аудиодорожки")
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: throw IOException("Неизвестный формат")
        val duration = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val out = FileOutputStream(dest)
        out.write(ByteArray(44))
        val info = MediaCodec.BufferInfo()
        var inEos = false
        var outEos = false
        var total = 0L
        try {
            while (!outEos && !p.cancelled) {
                if (!inEos) {
                    val ii = codec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            if (duration > 0L) {
                                p.total = 100L
                                p.done = (ex.sampleTime * 100L / duration).coerceIn(0L, 100L)
                            }
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)!!
                    ob.position(info.offset)
                    ob.limit(info.offset + info.size)
                    val chunk = ByteArray(info.size)
                    ob.get(chunk)
                    out.write(chunk)
                    total += info.size
                    codec.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = codec.outputFormat
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            }
        } finally {
            out.close()
            codec.stop()
            codec.release()
            ex.release()
        }
        val raf = RandomAccessFile(dest, "rw")
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt((36 + total).toInt())
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1)
        h.putShort(channels.toShort())
        h.putInt(rate)
        h.putInt(rate * channels * 2)
        h.putShort((channels * 2).toShort())
        h.putShort(16)
        h.put("data".toByteArray())
        h.putInt(total.toInt())
        raf.seek(0)
        raf.write(h.array())
        raf.close()
    }

    /** Конвертация аудио/видео через Media3 Transformer (выход: MP4 или M4A). Вызывать с главного потока. */
    suspend fun transform(ctx: Context, src: File, dest: File, audioOnly: Boolean, p: Progress) {
        suspendCancellableCoroutine<Unit> { cont ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            }
            val b = Transformer.Builder(ctx).addListener(listener)
            if (!audioOnly) b.setVideoMimeType(MimeTypes.VIDEO_H264)
            b.setAudioMimeType(MimeTypes.AUDIO_AAC)
            val t = b.build()
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src))).setRemoveVideo(audioOnly).build()
            t.start(item, dest.absolutePath)
            val handler = Handler(Looper.getMainLooper())
            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    if (p.cancelled) {
                        t.cancel()
                        cont.cancel()
                        return
                    }
                    val st = t.getProgress(holder)
                    if (st != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        p.total = 100L
                        p.done = holder.progress.toLong()
                    }
                    handler.postDelayed(this, 300)
                }
            }
            handler.post(poll)
            cont.invokeOnCancellation { t.cancel() }
        }
    }
}
