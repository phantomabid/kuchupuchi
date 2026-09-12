package com.phantom.kuchupuchi

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.phantom.kuchupuchi.config.FlavorConfig
import com.phantom.kuchupuchi.config.PreferenceManager
import com.phantom.kuchupuchi.service.FloatingService
import com.phantom.kuchupuchi.service.VibrationPattern
import com.phantom.kuchupuchi.ui.theme.AppTheme
import com.phantom.kuchupuchi.ui.theme.KuchuPuchiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private var hasOverlayPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var isIgnoringBattery by mutableStateOf(true)
    private var isServiceRunning by mutableStateOf(false)

    private var selectedTheme by mutableStateOf(PreferenceManager.DEFAULT_THEME)
    private var selectedPattern by mutableStateOf(VibrationPattern.HEARTBEAT.id)
    private var idleOpacity by mutableFloatStateOf(PreferenceManager.DEFAULT_IDLE_OPACITY)
    private var buttonSizeDp by mutableIntStateOf(PreferenceManager.DEFAULT_BUTTON_SIZE_DP)

    private var selfPresenceStatus by mutableStateOf("active")
    private var selfLastSeen by mutableLongStateOf(0L)
    private var partnerPresenceStatus by mutableStateOf("offline")
    private var partnerLastSeen by mutableLongStateOf(0L)

    private var presenceRef: DatabaseReference? = null
    private var selfPresenceRef: DatabaseReference? = null
    private var selfPresenceListener: ValueEventListener? = null
    private var connectedRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var partnerPresenceRef: DatabaseReference? = null
    private var partnerPresenceListener: ValueEventListener? = null

    private var showOnboardingDialog by mutableStateOf(false)
    private var sosCooldownSeconds by mutableIntStateOf(0)
    private var pingCooldownSeconds by mutableIntStateOf(0)

    @get:SuppressLint("InvalidFragmentVersionForActivityResult")
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        updateStates()
    }

    @get:SuppressLint("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        updateStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        selectedTheme = PreferenceManager.getAppTheme(this)
        selectedPattern = PreferenceManager.getSelectedPattern(this)
        idleOpacity = PreferenceManager.getIdleOpacity(this)
        buttonSizeDp = PreferenceManager.getFloatingButtonSizeDp(this)

        updateStates()
        setupPartnerPresenceListener()

        val isFirst = PreferenceManager.isFirstLaunch(this)
        val permissionsMissing = !hasOverlayPermission || !hasNotificationPermission
        if (isFirst || permissionsMissing) {
            showOnboardingDialog = true
        }

        setContent {
            KuchuPuchiTheme(appTheme = selectedTheme) {
                DashboardScreen(
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    isIgnoringBattery = isIgnoringBattery,
                    isServiceRunning = isServiceRunning,
                    selfPresenceStatus = selfPresenceStatus,
                    selfLastSeen = selfLastSeen,
                    partnerPresenceStatus = partnerPresenceStatus,
                    partnerLastSeen = partnerLastSeen,
                    selectedThemeId = selectedTheme,
                    selectedPatternId = selectedPattern,
                    idleOpacity = idleOpacity,
                    buttonSizeDp = buttonSizeDp,
                    sosCooldownSeconds = sosCooldownSeconds,
                    pingCooldownSeconds = pingCooldownSeconds,
                    showOnboardingDialog = showOnboardingDialog,
                    onDismissOnboardingDialog = {
                        showOnboardingDialog = false
                        PreferenceManager.setFirstLaunchCompleted(this)
                    },
                    onGrantPermissionsClick = {
                        showOnboardingDialog = false
                        PreferenceManager.setFirstLaunchCompleted(this)
                        if (!hasOverlayPermission) {
                            requestOverlayPermission()
                        } else if (!hasNotificationPermission) {
                            requestNotificationPermission()
                        }
                    },
                    onToggleService = { enable -> toggleFloatingService(enable) },
                    onSelectTheme = { theme ->
                        selectedTheme = theme.id
                        PreferenceManager.setAppTheme(this, theme.id)
                    },
                    onSelectPattern = { pattern ->
                        selectedPattern = pattern.id
                        PreferenceManager.setSelectedPattern(this, pattern.id)
                    },
                    onIdleOpacityChange = { newOpacity ->
                        idleOpacity = newOpacity
                        PreferenceManager.setIdleOpacity(this, newOpacity)
                    },
                    onButtonSizeDpChange = { newSizeDp ->
                        buttonSizeDp = newSizeDp
                        PreferenceManager.setFloatingButtonSizeDp(this, newSizeDp)
                    },
                    onTriggerSos = { triggerEmergencySos() },
                    onSendPing = { sendPingSignal() },
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectedListener?.let { listener ->
            connectedRef?.removeEventListener(listener)
        }
        connectedListener = null

        selfPresenceListener?.let { listener ->
            selfPresenceRef?.removeEventListener(listener)
        }
        selfPresenceListener = null

        partnerPresenceListener?.let { listener ->
            partnerPresenceRef?.removeEventListener(listener)
        }
        partnerPresenceListener = null
    }

    private fun setupPartnerPresenceListener() {
        try {
            val dbUrl = FlavorConfig.firebaseDbUrl.ifEmpty { FloatingService.DEFAULT_DATABASE_URL }
            val db = FirebaseDatabase.getInstance(dbUrl)

            val presenceRef = db.getReference("presence")
            presenceRef.keepSynced(true)
            this.presenceRef = presenceRef

            val selfPresenceRef = presenceRef.child(FlavorConfig.getSelfDbPath())
            this.selfPresenceRef = selfPresenceRef

            val selfListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        selfPresenceStatus = "offline"
                        selfLastSeen = 0L
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

                    val lastSeenRaw = map?.get("lastSeen")
                    val lastSeen = when (lastSeenRaw) {
                        is Long -> lastSeenRaw
                        is Double -> lastSeenRaw.toLong()
                        is Number -> lastSeenRaw.toLong()
                        else -> snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                    }

                    selfPresenceStatus = status
                    selfLastSeen = lastSeen
                }

                override fun onCancelled(error: DatabaseError) {
                    selfPresenceStatus = "offline"
                }
            }
            this.selfPresenceListener = selfListener
            selfPresenceRef.addValueEventListener(selfListener)

            val connectedRef = db.getReference(".info/connected")
            this.connectedRef = connectedRef

            val connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        selfPresenceRef.child("status").onDisconnect().setValue("offline")
                        selfPresenceRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

                        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
                        val currentStatus = if (keyguardManager?.isKeyguardLocked == true) "away" else "active"
                        val updates = mapOf(
                            "status" to currentStatus,
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

            val partnerPresenceRef = presenceRef.child(FlavorConfig.getPartnerDbPath())
            this.partnerPresenceRef = partnerPresenceRef

            val partnerListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        partnerPresenceStatus = "offline"
                        partnerLastSeen = 0L
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

                    val lastSeenRaw = map?.get("lastSeen")
                    val lastSeen = when (lastSeenRaw) {
                        is Long -> lastSeenRaw
                        is Double -> lastSeenRaw.toLong()
                        is Number -> lastSeenRaw.toLong()
                        else -> snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                    }

                    partnerPresenceStatus = status
                    partnerLastSeen = lastSeen
                }

                override fun onCancelled(error: DatabaseError) {
                    partnerPresenceStatus = "offline"
                }
            }
            this.partnerPresenceListener = partnerListener
            partnerPresenceRef.addValueEventListener(partnerListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateStates() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        isIgnoringBattery = checkIsIgnoringBattery()
        isServiceRunning = checkIsServiceRunning()
    }

    private fun checkIsIgnoringBattery(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun checkIsServiceRunning(): Boolean {
        if (FloatingService.isRunning) return true
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun toggleFloatingService(enable: Boolean) {
        if (enable) {
            if (!hasOverlayPermission) {
                requestOverlayPermission()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                requestNotificationPermission()
                return
            }
            val intent = Intent(this, FloatingService::class.java)
            ContextCompat.startForegroundService(this, intent)
            isServiceRunning = true
        } else {
            val intent = Intent(this, FloatingService::class.java)
            stopService(intent)
            isServiceRunning = false
        }
    }

    private fun triggerEmergencySos() {
        if (sosCooldownSeconds > 0) return

        sosCooldownSeconds = 10
        lifecycleScope.launch {
            while (sosCooldownSeconds > 0) {
                delay(1000L)
                sosCooldownSeconds--
            }
        }

        try {
            val dbUrl = FlavorConfig.firebaseDbUrl.ifEmpty { FloatingService.DEFAULT_DATABASE_URL }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val sosRef = db.getReference("touches").child(FlavorConfig.getSelfDbPath()).child("sos")
            val sosData = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "trigger" to true,
            )
            sosRef.setValue(sosData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendPingSignal() {
        if (pingCooldownSeconds > 0) return

        pingCooldownSeconds = 5
        lifecycleScope.launch {
            while (pingCooldownSeconds > 0) {
                delay(1000L)
                pingCooldownSeconds--
            }
        }

        try {
            val dbUrl = FlavorConfig.firebaseDbUrl.ifEmpty { FloatingService.DEFAULT_DATABASE_URL }
            val db = FirebaseDatabase.getInstance(dbUrl)
            val pingRef = db.getReference("touches").child(FlavorConfig.getSelfDbPath()).child("ping")
            val pingData = mapOf("timestamp" to System.currentTimeMillis())
            pingRef.setValue(pingData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:$packageName".toUri(),
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    isIgnoringBattery: Boolean,
    isServiceRunning: Boolean,
    selfPresenceStatus: String = "active",
    selfLastSeen: Long = 0L,
    partnerPresenceStatus: String = "offline",
    partnerLastSeen: Long = 0L,
    selectedThemeId: String,
    selectedPatternId: String,
    idleOpacity: Float,
    buttonSizeDp: Int,
    sosCooldownSeconds: Int,
    pingCooldownSeconds: Int,
    showOnboardingDialog: Boolean,
    onDismissOnboardingDialog: () -> Unit,
    onGrantPermissionsClick: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onSelectTheme: (AppTheme) -> Unit,
    onSelectPattern: (VibrationPattern) -> Unit,
    onIdleOpacityChange: (Float) -> Unit,
    onButtonSizeDpChange: (Int) -> Unit,
    onTriggerSos: () -> Unit,
    onSendPing: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
) {
    if (showOnboardingDialog) {
        PermissionOnboardingDialog(
            hasOverlayPermission = hasOverlayPermission,
            hasNotificationPermission = hasNotificationPermission,
            onDismiss = onDismissOnboardingDialog,
            onGrant = onGrantPermissionsClick,
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroHeaderCard()

            PartnerPresenceCard(
                partnerStatus = partnerPresenceStatus,
                lastSeen = partnerLastSeen,
            )

            SelfPresenceCard(
                selfStatus = selfPresenceStatus,
                lastSeen = selfLastSeen,
            )

            PingAudioCard(
                pingCooldownSeconds = pingCooldownSeconds,
                onSendPing = onSendPing,
            )

            EmergencySosCard(
                sosCooldownSeconds = sosCooldownSeconds,
                onTriggerSos = onTriggerSos,
            )

            ServiceControlCard(
                isServiceRunning = isServiceRunning,
                onToggleService = onToggleService,
            )

            BatteryOptimizationCard(
                isIgnoringBattery = isIgnoringBattery,
                onRequestBatteryExemption = onRequestBatteryExemption,
            )

            AppThemeSelectorCard(
                selectedThemeId = selectedThemeId,
                onSelectTheme = onSelectTheme,
            )

            VibrationPatternSelectorCard(
                selectedPatternId = selectedPatternId,
                onSelectPattern = onSelectPattern,
            )

            IdleOpacityCard(
                idleOpacity = idleOpacity,
                onIdleOpacityChange = onIdleOpacityChange,
            )

            FloatingButtonSizeCard(
                buttonSizeDp = buttonSizeDp,
                onButtonSizeDpChange = onButtonSizeDpChange,
            )

            PermissionsCard(
                hasOverlayPermission = hasOverlayPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Made with love by Mahi 💖✨ • Inspired by my girlfriend Samia 👩‍❤️‍👨🏽🌸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
fun HeroHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pets,
                    contentDescription = "Companion Icon",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "KuchuPuchi Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Self: ${FlavorConfig.flavorName} • Partner: ${FlavorConfig.partnerFlavorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun PartnerPresenceCard(
    partnerStatus: String,
    lastSeen: Long,
) {
    val partnerIconRes = FlavorConfig.getPartnerIconResId(partnerStatus)

    val (dotColor, statusText) = when (partnerStatus.lowercase(Locale.ROOT)) {
        "active" -> Color(0xFF4CAF50) to "Active Now"
        "away" -> Color(0xFFFFC107) to "Away (Screen Off/Locked)"
        else -> {
            val formattedTime = if (lastSeen > 0L) {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Offline • Last seen " + sdf.format(Date(lastSeen))
            } else {
                "Offline"
            }
            Color(0xFFF44336) to formattedTime
        }
    }

    val isOffline = partnerStatus.equals("offline", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(
                    painter = painterResource(id = partnerIconRes),
                    contentDescription = "Partner Avatar",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    colorFilter = if (isOffline) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null,
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "${FlavorConfig.partnerFlavorName.replaceFirstChar { it.uppercase() }} Presence",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SelfPresenceCard(
    selfStatus: String,
    lastSeen: Long,
) {
    val selfIconRes = FlavorConfig.getSelfIconResId(selfStatus)

    val (dotColor, statusText) = when (selfStatus.lowercase(Locale.ROOT)) {
        "active" -> Color(0xFF4CAF50) to "Active Now"
        "away" -> Color(0xFFFFC107) to "Away (Screen Off/Locked)"
        else -> {
            val formattedTime = if (lastSeen > 0L) {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                "Offline • Last seen " + sdf.format(Date(lastSeen))
            } else {
                "Offline"
            }
            Color(0xFFF44336) to formattedTime
        }
    }

    val isOffline = selfStatus.equals("offline", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(
                    painter = painterResource(id = selfIconRes),
                    contentDescription = "Self Avatar",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    colorFilter = if (isOffline) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null,
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "${FlavorConfig.flavorName.replaceFirstChar { it.uppercase() }} (You)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PingAudioCard(
    pingCooldownSeconds: Int,
    onSendPing: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Ping Partner Audio Signal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Play crisp echoed ting tone on partner device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Button(
                onClick = onSendPing,
                enabled = pingCooldownSeconds == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pingCooldownSeconds > 0) "PING SENT (${pingCooldownSeconds}s)" else "SEND PING TONE",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencySosCard(
    sosCooldownSeconds: Int,
    onTriggerSos: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Emergency SOS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "Alert partner with loud Morse code S.O.S tone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }

            Button(
                onClick = onTriggerSos,
                enabled = sosCooldownSeconds == 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.6f),
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (sosCooldownSeconds > 0) "SOS Cooldown (${sosCooldownSeconds}s)" else "SEND EMERGENCY SOS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceControlCard(
    isServiceRunning: Boolean,
    onToggleService: (Boolean) -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isServiceRunning) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "service_card_color",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceRunning) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                            contentDescription = null,
                            tint = if (isServiceRunning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Overlay Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isServiceRunning) "Floating companion active" else "Service stopped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isServiceRunning) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = if (isServiceRunning) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServiceRunning) "Overlay Service Running" else "Overlay Service Stopped",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationCard(
    isIgnoringBattery: Boolean,
    onRequestBatteryExemption: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isIgnoringBattery) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isIgnoringBattery) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Battery Optimization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isIgnoringBattery) "Exempted (Background touch active)" else "Not Exempted (May delay haptics)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (isIgnoringBattery) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (!isIgnoringBattery) {
                OutlinedButton(
                    onClick = onRequestBatteryExemption,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Request Battery Optimization Exemption", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppThemeSelectorCard(
    selectedThemeId: String,
    onSelectTheme: (AppTheme) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Dashboard Color Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Choose custom color palette for app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTheme.entries.forEach { theme ->
                    val isSelected = theme.id.equals(selectedThemeId, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTheme(theme) },
                        label = { Text(theme.displayName) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VibrationPatternSelectorCard(
    selectedPatternId: String,
    onSelectPattern: (VibrationPattern) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Vibration,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Vibration Pattern",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Select signal pattern sent on touch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VibrationPattern.entries.forEach { pattern ->
                    val isSelected = pattern.id.equals(selectedPatternId, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPattern(pattern) },
                        label = { Text(pattern.displayName) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            val currentPattern = VibrationPattern.fromId(selectedPatternId)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${currentPattern.displayName}: ${currentPattern.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
fun IdleOpacityCard(
    idleOpacity: Float,
    onIdleOpacityChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Opacity,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Idle Overlay Opacity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Set float window transparency when idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "${(idleOpacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                value = idleOpacity,
                onValueChange = onIdleOpacityChange,
                valueRange = 0.1f..1.0f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
            )
        }
    }
}

@Composable
fun FloatingButtonSizeCard(
    buttonSizeDp: Int,
    onButtonSizeDpChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Floating Button Size",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Set float window icon size in dp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "$buttonSizeDp dp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                value = buttonSizeDp.toFloat().coerceIn(16f, 120f),
                onValueChange = { onButtonSizeDpChange(it.roundToInt()) },
                valueRange = 16f..120f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
            )
        }
    }
}

@Composable
fun PermissionsCard(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "System Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            PermissionRowItem(
                icon = Icons.Rounded.Layers,
                title = "Display Over Other Apps",
                isGranted = hasOverlayPermission,
                onRequestPermission = onRequestOverlayPermission,
                buttonText = "Grant Overlay Permission",
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRowItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Notifications",
                    isGranted = hasNotificationPermission,
                    onRequestPermission = onRequestNotificationPermission,
                    buttonText = "Allow Notifications",
                )
            }
        }
    }
}

@Composable
fun PermissionRowItem(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    buttonText: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            Icon(
                imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = if (isGranted) "Granted" else "Pending",
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }

        if (!isGranted) {
            OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun PermissionOnboardingDialog(
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onDismiss: () -> Unit,
    onGrant: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Welcome to KuchuPuchi",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "To send and receive touch vibrations with your partner, KuchuPuchi needs the following permissions:",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (!hasOverlayPermission) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Display Over Other Apps (Overlay)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notifications (Foreground Service)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("Grant Permissions", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    KuchuPuchiTheme {
        DashboardScreen(
            hasOverlayPermission = true,
            hasNotificationPermission = true,
            isIgnoringBattery = false,
            isServiceRunning = true,
            selfPresenceStatus = "active",
            selfLastSeen = 0L,
            partnerPresenceStatus = "active",
            partnerLastSeen = 0L,
            selectedThemeId = "DEFAULT",
            selectedPatternId = "HEARTBEAT",
            idleOpacity = 0.5f,
            buttonSizeDp = 64,
            sosCooldownSeconds = 0,
            pingCooldownSeconds = 0,
            showOnboardingDialog = false,
            onDismissOnboardingDialog = {},
            onGrantPermissionsClick = {},
            onToggleService = {},
            onSelectTheme = {},
            onSelectPattern = {},
            onIdleOpacityChange = {},
            onButtonSizeDpChange = {},
            onTriggerSos = {},
            onSendPing = {},
            onRequestOverlayPermission = {},
            onRequestNotificationPermission = {},
            onRequestBatteryExemption = {},
        )
    }
}
