package com.familyshield.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.familyshield.app.ui.MainActivity

object Notifications {
    const val ID_PROTECTING = 1
    private const val ID_PROBLEM = 2
    private const val ID_UPDATE = 3

    private const val CH_PROTECTING = "protecting"
    private const val CH_PROBLEM = "problem"
    private const val CH_UPDATE = "update"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PROTECTING, "보호 중 표시", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_PROBLEM, "보호가 꺼졌을 때", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_UPDATE, "새 버전", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun protecting(context: Context): Notification =
        Notification.Builder(context, CH_PROTECTING)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("광고 방패 보호 중")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()

    /** 보호가 멈췄을 때. 화면에도 같은 내용이 나오지만, 앱을 안 열고 있어도 알 수 있게 알림도 띄운다 */
    fun problem(context: Context, text: String) {
        val n = Notification.Builder(context, CH_PROBLEM)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("광고 방패가 꺼졌습니다")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text\n눌러서 앱을 열고 '보호 켜기'를 누르세요."))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_PROBLEM, n)
    }

    fun clearProblem(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_PROBLEM)
    }

    fun installReady(context: Context, confirm: Intent) {
        val tap = PendingIntent.getActivity(
            context, 0, Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CH_UPDATE)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("광고 방패 새 버전")
            .setContentText("눌러서 '설치'를 누르세요")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_UPDATE, n)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
