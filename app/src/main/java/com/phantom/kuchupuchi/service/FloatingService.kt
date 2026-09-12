package com.phantom.kuchupuchi.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.phantom.kuchupuchi.R
import com.phantom.kuchupuchi.config.FlavorConfig
import com.phantom.kuchupuchi.config.PreferenceManager
import com.phantom.kuchupuchi.updater.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var floatingImageView: ImageView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var database: FirebaseDatabase? = null
    private var presenceRef: DatabaseReference? = null
    private var selfPresenceRef: DatabaseReference? = null
    private var partnerPresenceRef: DatabaseReference? = null
    private var partnerPresenceListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var currentPresenceStatus: String = "active"
    private var currentPartnerPresenceStatus: String = "active"
    private var touchesRef: DatabaseReference? = null
    private var selfRef: DatabaseReference? = null
    private var partnerRef: DatabaseReference? = null
    private var partnerListener: ValueEventListener? = null
    private var partnerSosListener: ValueEventListener? = null
    private var partnerPingListener: ValueEventListener? = null

    private var vibrator: Vibrator? = null
    private var isVibrating = false
    private var currentVibratingVolume: Float? = null
    private var currentVibratingPattern: String? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var pulseAnimator: ValueAnimator? = null

    private var presenceReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSeenRunnable: Runnable? = null

    private var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastProcessedSosTime: Long = System.currentTimeMillis()
    private var lastProcessedPingTime: Long = System.currentTimeMillis()

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupVibrator()
        setupWakeLock()
        setupPresenceReceiver()
        setupFirebase()

        touchStateListener = { active ->
            updateSelfTouchSignal(active)
        }

        prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceManager.KEY_IDLE_OPACITY) {
                if (!isTouchActive && !isVibrating) {
                    overlayView?.alpha = PreferenceManager.getIdleOpacity(this@FloatingService)
                }
            } else if (key == PreferenceManager.KEY_BUTTON_SIZE_DP) {
                updateFloatingButtonSize()
            }
        }
        PreferenceManager.getPreferences(this)
            .registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundType,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if ((overlayView == null) && Settings.canDrawOverlays(this)) {
            setupFloatingOverlay()
        }

        serviceScope.launch {
            try {
                UpdateManager(this@FloatingService).checkForUpdates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return START_STICKY
    }

    private fun setupVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun setupWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KuchuPuchi:PartnerTouchWakeLock",
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getCurrentPresenceStatus(): String {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: true
        return if (isInteractive && !isKeyguardLocked) "active" else "away"
    }

    private fun setupPresenceReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        presenceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false
                val status = when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> "active"
                    Intent.ACTION_SCREEN_OFF -> "away"
                    Intent.ACTION_SCREEN_ON -> if (isKeyguardLocked) "away" else "active"
                    else -> if (isKeyguardLocked) "away" else "active"
                }
                updatePresenceStatus(status)
            }
        }
        try {
            registerReceiver(presenceReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val initialStatus = getCurrentPresenceStatus()
        updatePresenceStatus(initialStatus)

        lastSeenRunnable = object : Runnable {
            override fun run() {
                try {
                    selfPresenceRef?.child("lastSeen")?.setValue(ServerValue.TIMESTAMP)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mainHandler.postDelayed(this, 60_000L)
            }
        }
        mainHandler.postDelayed(lastSeenRunnable!!, 60_000L)
    }

    private fun updatePresenceStatus(status: String) {
        currentPresenceStatus = status
        try {
            val selfRef = selfPresenceRef ?: return
            val updates = mapOf(
                "status" to status,
                "lastSeen" to ServerValue.TIMESTAMP,
            )
            selfRef.setValue(updates)
            if (status != "offline") {
                selfRef.child("status").onDisconnect().setValue("offline")
                selfRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFloatingIcon(status: String) {
        mainHandler.post {
            val iconRes = FlavorConfig.getPartnerIconResId(status)
            floatingImageView?.setImageResource(iconRes)
        }
    }

    private fun setupFirebase() {
        try {
            val dbUrl = FlavorConfig.firebaseDbUrl.ifEmpty { DEFAULT_DATABASE_URL }
            val db = FirebaseDatabase.getInstance(dbUrl)
            this.database = db

            val presenceRef = db.getReference("presence")
            presenceRef.keepSynced(true)
            this.presenceRef = presenceRef

            val selfPresenceRef = presenceRef.child(FlavorConfig.getSelfDbPath())
            this.selfPresenceRef = selfPresenceRef

            val partnerPresenceRef = presenceRef.child(FlavorConfig.getPartnerDbPath())
            this.partnerPresenceRef = partnerPresenceRef

            val partnerPresenceListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        currentPartnerPresenceStatus = "offline"
                        updateFloatingIcon("offline")
                        return
                    }

                    val map = snapshot.value as? Map<*, *>
                    val statusStr = (map?.get("status") as? String)
                        ?: snapshot.child("status").getValue(String::class.java)
                        ?: snapshot.getValue(String::class.java)
                        ?: "offline"

                    val status = when (statusStr.lowercase(Locale.ROOT)) {
                        "active" -> "active"
                        "away" -> "away"
                        "offline" -> "offline"
                        else -> "offline"
                    }

                    currentPartnerPresenceStatus = status
                    updateFloatingIcon(status)
                }

                override fun onCancelled(error: DatabaseError) {
                    currentPartnerPresenceStatus = "offline"
                    updateFloatingIcon("offline")
                }
            }
            this.partnerPresenceListener = partnerPresenceListener
            partnerPresenceRef.addValueEventListener(partnerPresenceListener)

            val connectedRef = db.getReference(".info/connected")
            this.connectedRef = connectedRef

            val connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        selfPresenceRef.child("status").onDisconnect().setValue("offline")
                        selfPresenceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                        val status = getCurrentPresenceStatus()
                        currentPresenceStatus = status
                        val updates = mapOf(
                            "status" to status,
                            "lastSeen" to ServerValue.TIMESTAMP,
                        )
                        selfPresenceRef.setValue(updates)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                }
            }
            this.connectedListener = connectedListener
            connectedRef.addValueEventListener(connectedListener)

            val touches = db.getReference("touches")
            touches.keepSynced(true)
            this.touchesRef = touches

            val self = touches.child(FlavorConfig.getSelfDbPath())
            val partner = touches.child(FlavorConfig.getPartnerDbPath())

            this.selfRef = self
            this.partnerRef = partner

            self.child("active").onDisconnect().setValue(false)

            val initialStatus = getCurrentPresenceStatus()
            updatePresenceStatus(initialStatus)

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        stopVibration()
                        return
                    }

                    val isPartnerActive = snapshot.child("active").getValue(Boolean::class.java)
                        ?: (snapshot.value as? Boolean)
                        ?: false

                    if (isPartnerActive) {
                        val volDouble = snapshot.child("volume").getValue(Double::class.java)
                            ?: snapshot.child("volume").getValue(Long::class.java)?.toDouble()
                            ?: 1.0
                        val volume = volDouble.toFloat().coerceIn(0f, 1f)
                        val pattern = snapshot.child("pattern").getValue(String::class.java)
                            ?: PreferenceManager.DEFAULT_PATTERN

                        startVibration(volume, pattern)
                    } else {
                        stopVibration()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    stopVibration()
                }
            }
            this.partnerListener = listener
            partner.addValueEventListener(listener)

            val sosRef = partner.child("sos")
            val sosListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java)
                        ?: snapshot.getValue(Long::class.java)
                        ?: 0L

                    if (timestamp > lastProcessedSosTime) {
                        lastProcessedSosTime = timestamp
                        playMorseSosTone()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            this.partnerSosListener = sosListener
            sosRef.addValueEventListener(sosListener)

            val pingRef = partner.child("ping")
            val pingListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val timestamp = snapshot.child("timestamp").getValue(Long::class.java)
                        ?: snapshot.getValue(Long::class.java)
                        ?: 0L

                    if (timestamp > lastProcessedPingTime) {
                        lastProcessedPingTime = timestamp
                        playTingSound()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }
            this.partnerPingListener = pingListener
            pingRef.addValueEventListener(pingListener)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playTingSound() {
        Thread {
            var audioTrack: AudioTrack? = null
            var audioManager: AudioManager? = null
            var originalVolume = -1
            try {
                audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                }

                val sampleRate = 44100
                val durationMs = 2000
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val sample = ShortArray(numSamples)
                val freq1 = 2400.0
                val freq2 = 4800.0

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val decayEnv = exp(-t * 2.5)
                    val signal = (sin(2.0 * PI * freq1 * t) + 0.35 * sin(2.0 * PI * freq2 * t)) * decayEnv
                    val clamped = (signal * 0.7).coerceIn(-1.0, 1.0)
                    sample[i] = (clamped * 32767).toInt().toShort()
                }

                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(max(minBufferSize, sample.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(sample, 0, sample.size)
                audioTrack.play()

                Thread.sleep(durationMs.toLong() + 100L)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (audioManager != null && originalVolume >= 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun playMorseSosTone() {
        Thread {
            var audioManager: AudioManager? = null
            var originalVolume = -1
            try {
                audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
                if (audioManager != null) {
                    originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                }

                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

                val shortDuration = 100L
                val longDuration = 250L
                val gapBetweenElements = 60L
                val gapBetweenLetters = 180L

                fun playTone(durationMs: Long) {
                    toneGen.startTone(ToneGenerator.TONE_CDMA_HIGH_L, durationMs.toInt())
                    Thread.sleep(durationMs + gapBetweenElements)
                }

                // Short Short Short (S: ...)
                playTone(shortDuration)
                playTone(shortDuration)
                playTone(shortDuration)

                Thread.sleep(gapBetweenLetters)

                // Long Long Long (O: ---)
                playTone(longDuration)
                playTone(longDuration)
                playTone(longDuration)

                Thread.sleep(gapBetweenLetters)

                // Short Short Short (S: ...)
                playTone(shortDuration)
                playTone(shortDuration)
                playTone(shortDuration)

                toneGen.release()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    if (audioManager != null && originalVolume >= 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun updateSelfTouchSignal(active: Boolean) {
        val self = selfRef
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val normalizedVolume = if (maxVolume > 0) {
                (currentVolume.toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
            } else {
                0.5f
            }

            val selectedPattern = PreferenceManager.getSelectedPattern(this)

            if (self != null) {
                val touchData = mapOf(
                    "active" to active,
                    "volume" to normalizedVolume,
                    "pattern" to selectedPattern,
                )
                self.setValue(touchData)
                if (active) {
                    self.child("active").onDisconnect().setValue(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setTouchActiveInternal(active)
    }

    private fun startPulseAnimation() {
        mainHandler.post {
            if (overlayView == null) return@post
            if (pulseAnimator != null) return@post

            val animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
                duration = 800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val v = overlayView ?: return@addUpdateListener
                    val angle = (anim.animatedValue as Float).toDouble()
                    val sineVal = ((sin(angle) + 1.0) / 2.0).toFloat()
                    val idleOpacity = PreferenceManager.getIdleOpacity(this@FloatingService)
                    v.scaleX = 1.0f + 0.25f * sineVal
                    v.scaleY = 1.0f + 0.25f * sineVal
                    v.alpha = idleOpacity + sineVal * (1.0f - idleOpacity)
                }
            }
            pulseAnimator = animator
            animator.start()
        }
    }

    private fun stopPulseAnimation() {
        mainHandler.post {
            pulseAnimator?.cancel()
            pulseAnimator = null
            overlayView?.let { view ->
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                if (!isTouchActive) {
                    view.alpha = PreferenceManager.getIdleOpacity(this@FloatingService)
                }
            }
        }
    }

    private fun startVibration(volume: Float, pattern: String) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        acquireWakeLock()
        startPulseAnimation()

        if (isVibrating && (currentVibratingVolume == volume) && (currentVibratingPattern == pattern)) {
            return
        }

        try {
            val waveform = VibrationPatternHelper.createWaveform(pattern, volume)
            val effect = VibrationEffect.createWaveform(
                waveform.timings,
                waveform.amplitudes,
                waveform.repeat,
            )
            vib.vibrate(effect)
            isVibrating = true
            currentVibratingVolume = volume
            currentVibratingPattern = pattern
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        releaseWakeLock()
        stopPulseAnimation()
        isVibrating = false
        currentVibratingVolume = null
        currentVibratingPattern = null
    }

    private fun setupFloatingOverlay() {
        val sizeDp = PreferenceManager.getFloatingButtonSizeDp(this)
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            resources.displayMetrics,
        ).toInt()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
        this.layoutParams = params

        val view = createOverlayView(params)
        this.overlayView = view

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFloatingButtonSize() {
        val view = overlayView ?: return
        val params = layoutParams ?: return
        val sizeDp = PreferenceManager.getFloatingButtonSizeDp(this)
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            resources.displayMetrics,
        ).toInt()

        view.layoutParams?.let {
            it.width = sizePx
            it.height = sizePx
            view.layoutParams = it
        }
        params.width = sizePx
        params.height = sizePx

        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView(params: WindowManager.LayoutParams): View {
        val sizeDp = PreferenceManager.getFloatingButtonSizeDp(this)
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            resources.displayMetrics,
        ).toInt()
        val paddingPx = 0
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setImageResource(FlavorConfig.getPartnerIconResId(currentPartnerPresenceStatus))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }
        floatingImageView = imageView

        val container = object : FrameLayout(this) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(204, 30, 27, 46))
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.argb(68, 255, 255, 255))
            }
            background = bgDrawable
            elevation = (8 * resources.displayMetrics.density)
            alpha = PreferenceManager.getIdleOpacity(this@FloatingService)
            addView(imageView)
        }

        container.setOnTouchListener(
            object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            isDragging = false
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            container.alpha = 1.0f // Active opacity (100%)
                            updateSelfTouchSignal(active = true)
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY

                            if (!isDragging && hypot(dx, dy) > touchSlop) {
                                isDragging = true
                            }

                            if (isDragging) {
                                params.x = initialX + dx.toInt()
                                params.y = initialY + dy.toInt()
                                try {
                                    windowManager?.updateViewLayout(container, params)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            container.alpha = 1.0f // Active opacity (100%)
                            updateSelfTouchSignal(active = true)
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            container.alpha = PreferenceManager.getIdleOpacity(this@FloatingService)
                            updateSelfTouchSignal(active = false)
                            if (event.actionMasked == MotionEvent.ACTION_UP && !isDragging) {
                                v?.performClick()
                            }
                            isDragging = false
                            return true
                        }
                    }
                    return false
                }
            },
        )

        return container
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_app_icon)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        isRunning = false
        updateSelfTouchSignal(active = false)

        presenceReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        presenceReceiver = null

        lastSeenRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        lastSeenRunnable = null

        updatePresenceStatus("offline")

        connectedListener?.let { listener ->
            connectedRef?.removeEventListener(listener)
        }
        connectedListener = null

        prefChangeListener?.let { listener ->
            PreferenceManager.getPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
        prefChangeListener = null

        partnerPresenceListener?.let { listener ->
            partnerPresenceRef?.removeEventListener(listener)
        }
        partnerPresenceListener = null
        partnerPresenceRef = null

        partnerListener?.let { listener ->
            partnerRef?.removeEventListener(listener)
        }
        partnerListener = null

        partnerSosListener?.let { listener ->
            partnerRef?.child("sos")?.removeEventListener(listener)
        }
        partnerSosListener = null

        partnerPingListener?.let { listener ->
            partnerRef?.child("ping")?.removeEventListener(listener)
        }
        partnerPingListener = null

        stopVibration()

        touchStateListener = null

        floatingImageView = null

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
        windowManager = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val CHANNEL_ID = "floating_service_channel"
        const val NOTIFICATION_ID = 1001
        const val DEFAULT_DATABASE_URL = "https://kuchupuchi-default-rtdb.asia-southeast1.firebasedatabase.app/"

        var isRunning: Boolean = false
            private set

        var isTouchActive: Boolean = false
            private set

        var touchStateListener: ((Boolean) -> Unit)? = null

        private fun setTouchActiveInternal(active: Boolean) {
            if (isTouchActive != active) {
                isTouchActive = active
                touchStateListener?.invoke(active)
            }
        }

        fun setTouchActive(active: Boolean) {
            setTouchActiveInternal(active)
        }
    }
}
