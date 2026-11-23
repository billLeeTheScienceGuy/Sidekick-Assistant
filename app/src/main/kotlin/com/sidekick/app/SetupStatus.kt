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
    val listenerConnected by MessageNotificationListener.connected.collectAsState()
    val apps by settings.messageApps.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var result by remember(permission, locationOn) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val reminders = (context.applicationContext as SidekickApplication).reminders
    val reminderReady = remember(refresh) { reminders.notificationsAllowed() }
    var reminderResult by remember { mutableStateOf("") }
    var schedulingReminder by remember { mutableStateOf(false) }
    Text(tr(R.string.setup_status))
    Text(tr(R.string.reminder_delivery_1_s, if (reminderReady) tr(R.string.enabled) else tr(R.string.blocked_in_android_settings)), modifier = Modifier.testTag("reminder-access-status"))
    Text(remember(refresh) { reminders.alertDescription() }, modifier = Modifier.testTag("reminder-alert-details"))
    TextButton(modifier = Modifier.testTag("reminder-channel-settings"), onClick = {
        context.startActivity(android.content.Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, com.sidekick.app.reminders.ReminderCoordinator.CHANNEL))
    }) { Text(tr(R.string.reminder_sound_vibration_pop_up)) }
    TextButton(enabled = reminderReady && !schedulingReminder, modifier = Modifier.testTag("test-reminder"), onClick = {
        scope.launch {
            schedulingReminder = true
            try {
                val due = reminders.scheduleCheck()
                val time = java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm z"))
                reminderResult = tr(R.string.test_reminder_set_for_1_s, time)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reminderResult = tr(R.string.could_not_schedule_the_check_enable) }
            finally { schedulingReminder = false }
        }
    }) { Text(tr(R.string.test_a_reminder_in_2_minutes)) }
    if (reminderResult.isNotBlank()) Text(reminderResult, modifier = Modifier.testTag("reminder-check-result"))
    Text(tr(R.string.location_permission_1_s, if (permission) tr(R.string.allowed) else tr(R.string.not_allowed)), modifier = Modifier.testTag("location-permission-status"))
    Text(tr(R.string.android_location_1_s, if (locationOn) tr(R.string.on) else tr(R.string.off)))
    Text(tr(R.string.message_scanning) + when {
        !scanning -> tr(R.string.off)
        !notificationAccess -> tr(R.string.needs_android_notification_access)
        apps.isEmpty() -> tr(R.string.choose_a_messaging_app)
        !listenerConnected -> tr(R.string.access_allowed_waiting_for_android_listener)
        else -> tr(R.string.ready_for_new_notifications_from_1, apps.size)
    }, modifier = Modifier.testTag("message-access-status"))
    if (scanning && notificationAccess && !listenerConnected) TextButton(onClick = {
        MessageNotificationListener.requestConnection(context)
    }, modifier = Modifier.testTag("reconnect-messages")) { Text(tr(R.string.reconnect_message_notifications)) }
    TextButton(enabled = permission && locationOn && !checking, modifier = Modifier.testTag("test-phone-location"), onClick = {
        scope.launch {
            checking = true; result = tr(R.string.checking_phone_location)
            try {
                val fix = PlaceLookup(context).currentLocation()
                result = if (fix == null) tr(R.string.no_recent_location_fix_try_again)
                else {
                    val age = ((SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000_000).coerceAtLeast(0)
                    tr(R.string.phone_location_works_approximate_accuracy_1, fix.accuracy.toInt(), age)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { result = tr(R.string.location_check_could_not_finish_try) }
            finally { checking = false }
        }
    }) { Text(if (checking) tr(R.string.checking) else tr(R.string.test_phone_location)) }
    if (result.isNotBlank()) Text(result)
}
