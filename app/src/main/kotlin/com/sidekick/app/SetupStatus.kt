package com.sidekick.app

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SetupStatus(settings: GuidanceSettings) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = remember(refresh) { context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED }
    val locationOn = remember(refresh) {
        val manager = context.getSystemService(LocationManager::class.java)
        runCatching { if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled else manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    }
    val notificationAccess = remember(refresh) {
        val component = ComponentName(context, MessageNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty().split(':').contains(component.flattenToString())
    }
    val scanning by settings.messageNotifications.collectAsState()
    val apps by settings.messageApps.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var result by remember(permission, locationOn) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val reminders = (context.applicationContext as SidekickApplication).reminders
    val reminderReady = remember(refresh) { reminders.notificationsAllowed() }
    var reminderResult by remember { mutableStateOf("") }
    var schedulingReminder by remember { mutableStateOf(false) }
    Text("Setup status")
    Text("Reminder delivery: ${if (reminderReady) "Enabled" else "Blocked in Android settings"}", modifier = Modifier.testTag("reminder-access-status"))
    Text(remember(refresh) { reminders.alertDescription() }, modifier = Modifier.testTag("reminder-alert-details"))
    TextButton(modifier = Modifier.testTag("reminder-channel-settings"), onClick = {
        context.startActivity(android.content.Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, com.sidekick.app.reminders.ReminderCoordinator.CHANNEL))
    }) { Text("Reminder sound, vibration & pop-up") }
    TextButton(enabled = reminderReady && !schedulingReminder, modifier = Modifier.testTag("test-reminder"), onClick = {
        scope.launch {
            schedulingReminder = true
            try {
                val due = reminders.scheduleCheck()
                val time = java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm z"))
                reminderResult = "Test reminder set for $time. Close Settings and lock the phone. Android may delay delivery; Undo in Planner removes the check."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reminderResult = "Could not schedule the check. Enable reminder notifications and try again." }
            finally { schedulingReminder = false }
        }
    }) { Text("Test a reminder in 2 minutes") }
    if (reminderResult.isNotBlank()) Text(reminderResult, modifier = Modifier.testTag("reminder-check-result"))
    Text("Location permission: ${if (permission) "Allowed" else "Not allowed"}", modifier = Modifier.testTag("location-permission-status"))
    Text("Android Location: ${if (locationOn) "On" else "Off"}")
    Text("Message scanning: " + when {
        !scanning -> "Off"
        !notificationAccess -> "Needs Android notification access"
        apps.isEmpty() -> "Choose a messaging app"
        else -> "Ready for new notifications from ${apps.size} selected app(s)"
    }, modifier = Modifier.testTag("message-access-status"))
    TextButton(enabled = permission && locationOn && !checking, modifier = Modifier.testTag("test-phone-location"), onClick = {
        scope.launch {
            checking = true; result = "Checking phone location…"
            try {
                val fix = PlaceLookup(context).currentLocation()
                result = if (fix == null) "No recent location fix. Try again or use a chosen city."
                else {
                    val age = ((SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000_000).coerceAtLeast(0)
                    "Phone location works: approximate accuracy ${fix.accuracy.toInt()} m; fix $age seconds old."
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = "Location check could not finish. Try again or use a chosen city." }
            finally { checking = false }
        }
    }) { Text(if (checking) "Checking…" else "Test phone location") }
    if (result.isNotBlank()) Text(result)
}
