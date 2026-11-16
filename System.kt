package com.thinktwice.app

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

data class AppEntry(val pkg: String, val label: String)

object Perms {
    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** El servicio figura como habilitado en los ajustes del sistema. */
    fun accessibilityEnabled(ctx: Context): Boolean {
        val on = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!on) return false
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, GuardService::class.java).flattenToString()
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }

    /** El servicio está realmente conectado y en ejecución en este proceso. */
    fun serviceRunning(): Boolean = GuardService.instance != null

    /** Refuerzo opcional: exime a la app de la restricción de abrir actividades desde segundo plano. */
    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun notifications(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
}

object Apps {
    /** Apps lanzables, sin la propia, ajustes ni teléfono: nunca se restringen funciones esenciales. */
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .filter { !isEssential(ctx, it.pkg) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun isEssential(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg == "com.android.systemui" || pkg == "com.android.settings" ||
            pkg.contains("emergency") || pkg == "com.google.android.dialer" || pkg == "com.android.dialer" ||
            pkg == "com.android.phone" || pkg == "com.android.server.telecom" || pkg == "com.android.packageinstaller" ||
            pkg == "com.google.android.packageinstaller") return true
        val dialer = try { (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage } catch (_: Exception) { null }
        return pkg == dialer
    }

    fun imePackages(ctx: Context): Set<String> = try {
        (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.map { it.packageName }.toSet()
    } catch (_: Exception) { emptySet() }
}

object UsageRepo {
    /**
     * Tiempo en primer plano por paquete en un día natural, reconstruido con eventos de uso
     * (ACTIVITY_RESUMED = 1; ACTIVITY_PAUSED = 2; ACTIVITY_STOPPED = 23). Android solo conserva los eventos
     * unos días: más atrás el resultado queda vacío y la interfaz lo muestra como "sin datos".
     */
    fun dayUsageMs(ctx: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault(), launchable: Set<String>? = null): Map<String, Long> {
        if (!Perms.usageAccess(ctx)) return emptyMap()
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(dayEnd, now)
        if (end <= start) return emptyMap()
        val ev = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        fun close(key: String, at: Long) {
            val st = open.remove(key) ?: return
            val pkg = key.substringBefore('/')
            total[pkg] = (total[pkg] ?: 0L) + (at - st).coerceAtLeast(0)
        }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val key = e.packageName + "/" + (e.className ?: "")
            when (e.eventType) {
                1 -> open[key] = e.timeStamp
                2, 23 -> close(key, e.timeStamp)
            }
        }
        open.keys.toList().forEach { close(it, end) }   // lo que sigue abierto al final del tramo consultado
        return total.filter { it.value >= 1000 && it.key != ctx.packageName && (launchable == null || it.key in launchable) }
    }
}

object Notifier {
    private const val CH_SESSION = "session"
    private const val CH_REMINDER = "reminder"

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "Sesiones activas", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "Recordatorios de retos", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Notificación con cuenta atrás gestionada por el propio sistema (cronómetro), válida aunque la app esté cerrada. */
    fun showSession(ctx: Context, s: Session) {
        if (!Perms.notifications(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(s.name)
            .setContentText("Sesión activa · termina a las ${hhmmFromMs(s.endMs)}")
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(s.endMs).setShowWhen(true)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setTimeoutAfter((s.endMs - System.currentTimeMillis()).coerceAtLeast(1000))
            .setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(s.id.hashCode(), n)
    }

    fun cancelSession(ctx: Context, id: String) =
        ctx.getSystemService(NotificationManager::class.java).cancel(id.hashCode())

    fun cancelAll(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancelAll()

    fun showReminder(ctx: Context) {
        if (!Perms.notifications(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("thinktwice")
            .setContentText("Tienes un reto en curso. Echa un vistazo a cómo va hoy.")
            .setAutoCancel(true).setContentIntent(openApp(ctx)).build()
        ctx.getSystemService(NotificationManager::class.java).notify(777, n)
    }

    private fun reminderIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Recordatorio diario aproximado (alarma inexacta: Android puede retrasarlo para ahorrar batería). */
    fun scheduleReminder(ctx: Context, hour: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC, c.timeInMillis, AlarmManager.INTERVAL_DAY, reminderIntent(ctx))
    }

    fun cancelReminder(ctx: Context) =
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(reminderIntent(ctx))
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        val s = Store.state.value
        val now = System.currentTimeMillis()
        val active = s.challenges.any { now < it.startMs + it.hours * 3_600_000L }
        if (s.remindersOn && active) Notifier.showReminder(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(ctx)
        val s = Store.state.value
        if (s.remindersOn) Notifier.scheduleReminder(ctx, s.reminderHour)
        val now = System.currentTimeMillis()
        s.sessions.filter { it.isActive(now) }.forEach { Notifier.showSession(ctx, it) }
    }
}
