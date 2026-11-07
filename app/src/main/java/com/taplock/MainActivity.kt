package com.taplock

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val store by lazy { LockStore(applicationContext) }
    private var nfcAdapter: NfcAdapter? = null
    private var scanMessage by mutableStateOf("Keep TapLock open, then tap an NFC card")
    private var accessibilityEnabled by mutableStateOf(false)
    private var scanEnabled = true
    private var lastUid: String? = null
    private var lastScanAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        accessibilityEnabled = isBlockingServiceEnabled()
        handleNfcIntent(intent)
        setContent {
            TapLockScreen(this, store, nfcAdapter != null, accessibilityEnabled, { scanMessage }, { openAccessibilitySettings() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        scanEnabled = true
        accessibilityEnabled = isBlockingServiceEnabled()
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        scanEnabled = false
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onTagDiscovered(tag: Tag) = processTag(tag)

    private fun handleNfcIntent(intent: Intent?) {
        @Suppress("DEPRECATION")
        val tag = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
        if (tag != null) processTag(tag)
    }

    private fun processTag(tag: Tag) {
        val uid = tag.id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        val now = System.currentTimeMillis()
        if (uid.isBlank() || (uid == lastUid && now - lastScanAt < 1800)) return
        lastUid = uid
        lastScanAt = now
        lifecycleScope.launch {
            val state = store.state.first()
            if (state.cardUid.isNullOrBlank()) {
                store.register(uid)
                scanMessage = "Card registered: $uid"
            } else if (state.cardUid == uid) {
                store.toggle(state.durationMinutes)
                scanMessage = if (store.state.first().isLocked) "Focus mode is on" else "Focus mode is off"
            } else {
                scanMessage = "Unknown card. Use the card registered in TapLock."
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isBlockingServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            val service = info.resolveInfo?.serviceInfo
            service?.packageName == packageName && service.name == BlockingAccessibilityService::class.java.name
        }
    }
}

private data class PhoneApp(val packageName: String, val label: String)

private fun getLaunchableApps(context: Context): List<PhoneApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .asSequence()
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { PhoneApp(it.packageName, context.packageManager.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
        .toList()
}

private object TapColors {
    val background = Color(0xFFF4F6F2)
    val surface = Color.White
    val ink = Color(0xFF17231F)
    val muted = Color(0xFF6B7771)
    val green = Color(0xFF176B50)
    val deepGreen = Color(0xFF163A30)
    val paleGreen = Color(0xFFE4EFE8)
    val warning = Color(0xFFFFF0E9)
    val warningInk = Color(0xFF8D3826)
    val border = Color(0xFFE4E9E4)
}

@Composable
private fun TapLockScreen(
    context: Context,
    store: LockStore,
    hasNfc: Boolean,
    accessibilityEnabled: Boolean,
    scanMessage: () -> String,
    openAccessibility: () -> Unit
) {
    val state by store.state.collectAsState(initial = LockState())
    var settings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val duration = state.durationMinutes
    val apps = remember(context) { getLaunchableApps(context) }
    val filteredApps = remember(apps, search) {
        apps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    }
    val selectedNames = apps.filter { it.packageName in state.selected }.map { it.label }
    val active = state.isLocked

    LaunchedEffect(state.locked, state.lockUntilMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(Modifier.fillMaxSize(), color = TapColors.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(TapColors.deepGreen, CircleShape), contentAlignment = Alignment.Center) {
                            Text("T", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("TapLock", color = TapColors.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("Make room for what matters.", color = TapColors.muted, fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = { settings = !settings },
                        modifier = Modifier.background(TapColors.surface, CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, "Settings", tint = TapColors.ink)
                    }
                }
            }

            if (!accessibilityEnabled) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TapColors.warning),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("!", color = TapColors.warningInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Turn on app blocking", color = TapColors.warningInk, fontWeight = FontWeight.SemiBold)
                            Text("Enable TapLock in Android Accessibility settings to block apps.", color = TapColors.warningInk, fontSize = 12.sp)
                        }
                        TextButton(onClick = openAccessibility) { Text("Set up", color = TapColors.warningInk) }
                    }
                }
            }

            item {
                val hero = if (active) TapColors.deepGreen else TapColors.paleGreen
                val heroText = if (active) Color.White else TapColors.ink
                Card(
                    colors = CardDefaults.cardColors(containerColor = hero),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(22.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (active) Color(0xFF8DE0B5) else TapColors.green, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(if (active) "FOCUS SESSION" else "READY WHEN YOU ARE", color = heroText.copy(alpha = .78f), fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("${state.selected.size} apps", color = heroText.copy(alpha = .8f), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(if (active) "Focus is on." else "Your time is yours.", color = heroText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (active) state.lockUntilMillis?.let { "Unlocks in ${remainingTime(it, now)}" } ?: "Tap your card or unlock below when you're ready."
                            else "Choose a few apps to pause and start a session.",
                            color = heroText.copy(alpha = .78f),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = heroText.copy(alpha = .18f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (selectedNames.isEmpty()) "No apps selected yet"
                            else "PAUSED APPS  ·  ${selectedNames.take(3).joinToString(", ")}${if (selectedNames.size > 3) " +${selectedNames.size - 3}" else ""}",
                            color = heroText.copy(alpha = .9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                SectionHeading("Session length", "Choose how long your focus lasts")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    listOf(30, 60, 120).forEach { minutes ->
                        FilterChip(
                            selected = duration == minutes,
                            onClick = { (context as? ComponentActivity)?.lifecycleScope?.launch { store.setDuration(minutes) } },
                            label = { Text("${minutes} min") },
                            enabled = !active,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TapColors.paleGreen, selectedLabelColor = TapColors.green)
                        )
                    }
                }
                Button(
                    onClick = { (context as? ComponentActivity)?.lifecycleScope?.launch { store.toggle(if (active) null else duration) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0xFF9D3B32) else TapColors.green)
                ) {
                    Text(if (active) "End focus session" else "Start a ${duration}-minute session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TapColors.surface),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(TapColors.paleGreen, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Nfc, contentDescription = null, tint = TapColors.green)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NFC card", color = TapColors.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                when {
                                    !hasNfc -> "This phone has no NFC. Use the session button."
                                    state.cardUid.isNullOrBlank() -> "Tap a card while TapLock is open to register it."
                                    else -> "Card ending ${state.cardUid.orEmpty().takeLast(4)} · tap to toggle focus."
                                },
                                color = TapColors.muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (hasNfc) Text(scanMessage(), color = TapColors.green, fontSize = 11.sp, modifier = Modifier.padding(start = 74.dp, end = 17.dp, bottom = 13.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionHeading("Apps to pause", "Choose any installed app")
                    Text("${state.selected.size} selected", color = TapColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TapColors.muted) },
                    placeholder = { Text("Search your apps", color = TapColors.muted) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TapColors.green,
                        unfocusedBorderColor = TapColors.border,
                        focusedContainerColor = TapColors.surface,
                        unfocusedContainerColor = TapColors.surface,
                        cursorColor = TapColors.green
                    )
                )
                if (active) Text("End the session to change app selections.", color = TapColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
            }

            if (filteredApps.isEmpty()) item {
                Text("No matching apps found.", color = TapColors.muted, modifier = Modifier.padding(vertical = 12.dp))
            }

            items(filteredApps, key = { it.packageName }) { app ->
                val selected = app.packageName in state.selected
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) TapColors.paleGreen.copy(alpha = .7f) else TapColors.surface, RoundedCornerShape(18.dp))
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).background(if (selected) TapColors.green else Color(0xFFEFF1EE), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Text(app.label.take(1).uppercase(), color = if (selected) Color.White else TapColors.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = TapColors.ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (selected) "Included in focus session" else "Tap to add to your list", color = TapColors.muted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = selected,
                        onCheckedChange = { checked ->
                            val updated = if (checked) state.selected + app.packageName else state.selected - app.packageName
                            (context as? ComponentActivity)?.lifecycleScope?.launch { store.setSelected(updated) }
                        },
                        enabled = !active,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TapColors.green)
                    )
                }
            }

            if (settings) item {
                Card(colors = CardDefaults.cardColors(containerColor = TapColors.surface), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Settings", color = TapColors.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text("TapLock uses Android Accessibility to cover and close selected apps during focus sessions.", color = TapColors.muted, fontSize = 13.sp)
                        Button(onClick = openAccessibility, modifier = Modifier.fillMaxWidth().padding(top = 11.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = TapColors.green)) {
                            Text(if (accessibilityEnabled) "Accessibility is enabled" else "Enable app blocking")
                        }
                        TextButton(onClick = { (context as? ComponentActivity)?.lifecycleScope?.launch { store.clearCard() } }) { Text("Clear registered NFC card", color = TapColors.warningInk) }
                        Text("Android still lets users disable Accessibility, so TapLock is a focus aid rather than a tamper-proof lock.", color = TapColors.muted, fontSize = 11.sp)
                    }
                }
            }

            item {
                Text("Timed sessions can run while TapLock is in the background. NFC scans work when the phone has NFC.", color = TapColors.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = TapColors.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TapColors.muted, fontSize = 12.sp)
    }
}

private fun remainingTime(until: Long, now: Long): String {
    val remainingSeconds = ((until - now).coerceAtLeast(0L) + 999L) / 1000L
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "%02d:%02d".format(minutes, seconds)
}
