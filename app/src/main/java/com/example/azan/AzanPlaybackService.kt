package com.example.azan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.PrayerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

object AzanAlertState {
    private val _activePrayer = MutableStateFlow<PrayerName?>(null)
    val activePrayer: StateFlow<PrayerName?> = _activePrayer.asStateFlow()

    private val _playbackNotice = MutableStateFlow<String?>(null)
    val playbackNotice: StateFlow<String?> = _playbackNotice.asStateFlow()

    @Volatile
    private var lastClaimedKey: String? = null

    internal fun setPlaying(prayer: PrayerName?) {
        _activePrayer.value = prayer
    }

    internal fun postNotice(message: String) {
        _playbackNotice.value = message
    }

    fun clearNotice() {
        _playbackNotice.value = null
    }

    @Synchronized
    fun claimPrayerTrigger(context: Context, prayer: PrayerName, azanEpochMillis: Long): Boolean {
        if (azanEpochMillis <= 0L) return true
        val key = "${prayer.name}_$azanEpochMillis"
        if (lastClaimedKey == key) return false
        val prefs = context.applicationContext.getSharedPreferences("azan_dedup", Context.MODE_PRIVATE)
        if (prefs.getString("last_key", null) == key) {
            lastClaimedKey = key
            return false
        }
        lastClaimedKey = key
        prefs.edit().putString("last_key", key).apply()
        return true
    }
}

object CustomAzanClipStore {
    private const val FILE_NAME = "custom_hayya_alas_salah.audio"

    fun clipFile(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    fun hasCustomClip(context: Context): Boolean {
        val file = clipFile(context)
        return file.exists() && file.length() > 0L
    }

    fun saveFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val dest = clipFile(context)
            context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            dest.length() > 0L
        } catch (e: Exception) {
            Log.w("CustomAzanClipStore", "Failed to import custom clip: ${e.message}")
            false
        }
    }

    fun clear(context: Context) {
        val file = clipFile(context)
        if (file.exists()) {
            file.delete()
        }
    }
}

object AzanAudioCache {
    private const val TAG = "AzanAudioCache"
    private const val BOOSTED_WAV_NAME = "hayya_boosted_v1.wav"
    private const val BOOST_LINEAR_GAIN = 1.75

    fun cachedBoostedWav(context: Context): File? {
        val file = File(context.applicationContext.cacheDir, BOOSTED_WAV_NAME)
        return if (file.exists() && file.length() > 44L) file else null
    }

    @Synchronized
    fun ensureBoostedWav(context: Context): File? {
        val appContext = context.applicationContext
        val outFile = File(appContext.cacheDir, BOOSTED_WAV_NAME)
        if (outFile.exists() && outFile.length() > 44L) return outFile
        return decodeAndBoostBundledMp3(appContext, outFile)
    }

    private fun decodeAndBoostBundledMp3(context: Context, outFile: File): File? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            val afd = context.resources.openRawResourceFd(R.raw.hayya_alas_salah)
            extractor = MediaExtractor().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            afd.close()

            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val pcmOut = ByteArrayOutputStream(512 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(
                                    inIdx, 0, sampleSize, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.get(chunk)
                            val boosted = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                boostFloatPcmTo16Bit(chunk, gain = BOOST_LINEAR_GAIN)
                            } else {
                                boostPcm16Chunk(chunk, gain = BOOST_LINEAR_GAIN)
                            }
                            pcmOut.write(boosted)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }

                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        ) {
                            pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                }
            }

            val rawPcm = pcmOut.toByteArray()
            if (rawPcm.isEmpty()) return null
            writeWavFile(outFile, rawPcm, sampleRate, channelCount)
        } catch (e: Exception) {
            Log.w(TAG, "MP3 boost decode failed: ${e.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun boostPcm16Chunk(chunk: ByteArray, gain: Double): ByteArray {
        val sampleCount = chunk.size / 2
        val inBuf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val normalized = (inBuf.getShort() / 32768.0) * gain
            outBuf.putShort(softLimitToShort(normalized))
        }
        return outBuf.array()
    }

    private fun boostFloatPcmTo16Bit(chunk: ByteArray, gain: Double): ByteArray {
        val sampleCount = chunk.size / 4
        val inBuf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val normalized = inBuf.getFloat().toDouble() * gain
            outBuf.putShort(softLimitToShort(normalized))
        }
        return outBuf.array()
    }

    private fun softLimitToShort(normalized: Double): Short {
        val limited = if (abs(normalized) > 0.85) {
            val sgn = sign(normalized)
            sgn * (0.85 + 0.15 * tanh((abs(normalized) - 0.85) / 0.15))
        } else {
            normalized
        }
        return (limited * 32767.0).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    private fun writeWavFile(
        file: File,
        pcmBytes: ByteArray,
        sampleRate: Int,
        channelCount: Int
    ): File {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        val dataBytes = pcmBytes.size
        val byteRate = sampleRate * channelCount * 2
        val blockAlign = (channelCount * 2).toShort()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channelCount.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()

        FileOutputStream(tempFile).use { out ->
            out.write(header)
            out.write(pcmBytes)
        }
        if (file.exists()) file.delete()
        tempFile.renameTo(file)
        return file
    }
}

class AzanPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var playbackJob: Job? = null
    private var isTestTrigger = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var timeoutJob: Job? = null
    private var isStopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAzanAndFinish()
            return START_NOT_STICKY
        }

        val prayerStr = intent?.getStringExtra(EXTRA_PRAYER_NAME)
        val prayer = PrayerName.entries.firstOrNull { it.name == prayerStr } ?: PrayerName.FAJR
        val jamaatOffset = intent?.getIntExtra(EXTRA_JAMAAT_OFFSET_MIN, prayer.defaultJamaatOffsetMin)
            ?: prayer.defaultJamaatOffsetMin
        isTestTrigger = intent?.getBooleanExtra(EXTRA_IS_TEST, false) ?: false

        isStopping = false
        AzanAlertState.setPlaying(prayer)

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification(prayer, jamaatOffset))

        val (usage, stream) = resolveUsageAndStream()
        requestAzanAudioFocus(usage)

        if (isTestTrigger) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.getStreamVolume(stream) == 0) {
                AzanAlertState.postNotice("Turn up Media volume to hear Hayya 'alas-salah.")
            }
        }

        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(20_000L)
            stopAzanAndFinish()
        }

        playbackJob?.cancel()
        releaseMediaPlayer()

        if (CustomAzanClipStore.hasCustomClip(this)) {
            playFileWithMediaPlayer(
                file = CustomAzanClipStore.clipFile(this),
                usage = usage,
                applyEnhancer = true,
                onFinished = { stopAzanAndFinish() },
                onError = { startBoostedBundledPlayback(usage) }
            )
        } else {
            val readyCached = AzanAudioCache.cachedBoostedWav(this)
            if (readyCached != null) {
                playFileWithMediaPlayer(
                    file = readyCached,
                    usage = usage,
                    applyEnhancer = false,
                    onFinished = { stopAzanAndFinish() },
                    onError = { playBundledRawWithEnhancer(usage) }
                )
            } else {
                startBoostedBundledPlayback(usage)
            }
        }

        return START_NOT_STICKY
    }

    private fun startBoostedBundledPlayback(usage: Int) {
        playbackJob = serviceScope.launch {
            val boostedFile = withContext(Dispatchers.IO) {
                AzanAudioCache.ensureBoostedWav(this@AzanPlaybackService)
            }
            if (isStopping) return@launch
            if (boostedFile != null) {
                playFileWithMediaPlayer(
                    file = boostedFile,
                    usage = usage,
                    applyEnhancer = false,
                    onFinished = { stopAzanAndFinish() },
                    onError = { playBundledRawWithEnhancer(usage) }
                )
            } else {
                playBundledRawWithEnhancer(usage)
            }
        }
    }

    private fun playBundledRawWithEnhancer(usage: Int) {
        if (isStopping) return
        releaseMediaPlayer()
        try {
            val afd = resources.openRawResourceFd(R.raw.hayya_alas_salah)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    releaseMediaPlayer()
                    stopAzanAndFinish()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Bundled Azan MediaPlayer error what=$what extra=$extra")
                    releaseMediaPlayer()
                    AzanAlertState.postNotice("Unable to play Azan audio on this device.")
                    stopAzanAndFinish()
                    true
                }
                prepare()
                attachLoudnessEnhancer(audioSessionId, gainMb = 550)
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play bundled Hayya 'alas-salah MP3", e)
            releaseMediaPlayer()
            AzanAlertState.postNotice("Unable to play Azan audio: ${e.message}")
            stopAzanAndFinish()
        }
    }

    private fun playFileWithMediaPlayer(
        file: File,
        usage: Int,
        applyEnhancer: Boolean,
        onFinished: () -> Unit,
        onError: () -> Unit
    ) {
        releaseMediaPlayer()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    releaseMediaPlayer()
                    onFinished()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "File MediaPlayer error what=$what extra=$extra")
                    releaseMediaPlayer()
                    onError()
                    true
                }
                prepare()
                if (applyEnhancer) {
                    attachLoudnessEnhancer(audioSessionId, gainMb = 550)
                }
                start()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.w(TAG, "File playback failed: ${e.message}")
            releaseMediaPlayer()
            onError()
        }
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int, gainMb: Int) {
        runCatching {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gainMb)
                enabled = true
            }
        }.onFailure { e ->
            Log.w(TAG, "LoudnessEnhancer unavailable: ${e.message}")
        }
    }

    private fun resolveUsageAndStream(): Pair<Int, Int> {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (isTestTrigger || isRunningOnEmulator()) {
            return AudioAttributes.USAGE_MEDIA to AudioManager.STREAM_MUSIC
        }
        val alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        return if (alarmVol > 0) {
            AudioAttributes.USAGE_ALARM to AudioManager.STREAM_ALARM
        } else {
            AudioAttributes.USAGE_MEDIA to AudioManager.STREAM_MUSIC
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("cutf") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("emulator")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NamazTime:AzanPlayback").apply {
            setReferenceCounted(false)
            acquire(25_000L)
        }
    }

    private fun requestAzanAudioFocus(usage: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun abandonAzanAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            }
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Azan & Jamaat Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Plays Hayya 'alas-salah and displays Jamaat countdowns when Azan begins."
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(prayer: PrayerName, jamaatOffsetMin: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AzanPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${prayer.displayName} Azan • Hayya 'alas-salah")
            .setContentText("Jamaat in $jamaatOffsetMin min • Tap to view live countdown")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_azan_label), stopIntent)
            .build()
    }

    private fun releaseMediaPlayer() {
        runCatching {
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        mediaPlayer = null
    }

    private fun stopAzanAndFinish() {
        if (isStopping) return
        isStopping = true
        timeoutJob?.cancel()
        playbackJob?.cancel()
        releaseMediaPlayer()
        abandonAzanAudioFocus()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
        AzanAlertState.setPlaying(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
        abandonAzanAudioFocus()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        AzanAlertState.setPlaying(null)
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AzanPlaybackService"
        private const val LEGACY_CHANNEL_ID = "azan_alerts"
        private const val CHANNEL_ID = "azan_alerts_silent"
        private const val NOTIFICATION_ID = 4041
        const val ACTION_STOP = "com.example.azan.ACTION_STOP"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_JAMAAT_OFFSET_MIN = "extra_jamaat_offset_min"
        const val EXTRA_AZAN_EPOCH_MILLIS = "extra_azan_epoch_millis"
        const val EXTRA_IS_TEST = "extra_is_test"

        fun triggerNow(
            context: Context,
            prayer: PrayerName,
            jamaatOffsetMin: Int,
            isTest: Boolean = false
        ) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                putExtra(EXTRA_PRAYER_NAME, prayer.name)
                putExtra(EXTRA_JAMAAT_OFFSET_MIN, jamaatOffsetMin)
                putExtra(EXTRA_IS_TEST, isTest)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stopNow(context: Context) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
