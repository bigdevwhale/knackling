package app.knackling.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.knackling.MainActivity
import app.knackling.R
import app.knackling.container
import app.knackling.domain.Book
import app.knackling.domain.Lang
import app.knackling.domain.Mood
import app.knackling.domain.today
import app.knackling.domain.tr
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private fun allowed(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
private fun open(ctx: Context) = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

/** The running session in the shade: the hobby, a ticking clock; tapping opens the timer. */
object LiveNote {
    private const val CH = "live"
    private const val ID = 7

    @android.annotation.SuppressLint("MissingPermission") // allowed() checks it
    suspend fun show(ctx: Context) {
        if (!allowed(ctx)) return
        val c = ctx.container
        val s = c.settings.now(); val h = s.live?.let { l -> c.dao.hobbyList().firstOrNull { it.id == l.hobbyId } }
        val l = s.live ?: return hide(ctx)
        Lang.ru = ctx.resources.configuration.locales[0].language == "ru"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, tr("Идёт занятие", "Session running"), NotificationManager.IMPORTANCE_LOW))
        val b = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat).setOngoing(true).setOnlyAlertOnce(true)
            .setContentTitle(if (h != null) "${h.label} · ${h.name}" else tr("Занятие", "Session"))
            .setContentIntent(open(ctx)).setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        if (l.paused) b.setContentText(tr("На паузе", "Paused"))
        else b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - l.ms()).setContentText(tr("Подмастерье смотрит, как ты работаешь", "Your apprentice is watching you work"))
        NotificationManagerCompat.from(ctx).notify(ID, b.build())
    }

    fun hide(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}

/**
 * The gentle nudge: once a day at the chosen hour, if nothing was done today and an apprentice has been
 * missing its hobby for a few days, it says so. Never more than one a day, never on a day with a session.
 */
class Nudge(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.now()
        if (!s.onboarded || s.nudge < 0 || LocalDateTime.now().hour != s.nudge || s.live != null) return Result.success()
        val b = Book(c.dao.hobbyList(), c.dao.sessionList(), c.dao.projectList(), s, today())
        if (b.sessions.any { it.day == b.today }) return Result.success()
        val h = b.active.filter { b.mood(it) == Mood.MISSING }.minByOrNull { b.lastDay(it) ?: 0 } ?: return Result.success()
        Lang.ru = applicationContext.resources.configuration.locales[0].language == "ru"
        show(applicationContext, tr("${h.name} скучает", "${h.name} misses you"),
            tr("${h.label}: хотя бы десять минут сегодня? ${b.idea(h).text}.", "${h.label}: ten minutes today? ${b.idea(h).text}."))
        return Result.success()
    }

    companion object {
        private const val CH = "nudge"
        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("nudge", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Nudge>(1, TimeUnit.HOURS).build())
        }
        @android.annotation.SuppressLint("MissingPermission") // allowed() checks it
        fun show(ctx: Context, title: String, text: String) {
            if (!allowed(ctx)) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, tr("Подмастерья скучают", "Apprentices miss you"), NotificationManager.IMPORTANCE_DEFAULT))
            val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open(ctx)).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(1, n)
        }
    }
}
