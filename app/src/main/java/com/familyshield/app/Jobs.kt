package com.familyshield.app

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.familyshield.app.update.SelfUpdater
import kotlin.concurrent.thread

/**
 * 정기 작업 일정. 필요한 만큼만 (docs/절대준수.md — 상대 서버 예의):
 * - 직접 관리 목록: 6시간마다, 아무 망 (파일 몇 KB, 바뀐 게 없으면 304)
 * - 커뮤니티 목록: 하루 한 번, 와이파이처럼 요금 안 드는 망만 (파일 수 MB)
 * - 새 버전 확인: 하루 한 번 — 단, 자동 업데이트 스위치(lists/AUTO_UPDATE.txt)가 '켜기'일 때만 서버에 묻는다
 */
object Jobs {
    private const val OWN_LISTS = 1
    private const val COMMUNITY_LISTS = 2
    private const val SELF_UPDATE = 3
    private const val FIRST_COMMUNITY = 4

    private const val HOUR = 60 * 60 * 1000L

    fun scheduleAll(context: Context) {
        periodic(context, OWN_LISTS, 6 * HOUR, JobInfo.NETWORK_TYPE_ANY)
        periodic(context, COMMUNITY_LISTS, 24 * HOUR, JobInfo.NETWORK_TYPE_UNMETERED)
        periodic(context, SELF_UPDATE, 24 * HOUR, JobInfo.NETWORK_TYPE_ANY)
        // 정기 작업은 첫 실행이 최대 하루 늦을 수 있어, 커뮤니티 목록이 아직 없으면 와이파이가 잡히는 대로 한 번 받는다
        val shield = context.shield
        if (shield.communityLists.any { !shield.store.file(it.id).exists() }) {
            once(context, FIRST_COMMUNITY, JobInfo.NETWORK_TYPE_UNMETERED)
        }
    }

    private fun periodic(context: Context, id: Int, interval: Long, network: Int) {
        val js = context.getSystemService(JobScheduler::class.java)
        val existing = js.getPendingJob(id)
        @Suppress("DEPRECATION")
        if (existing != null && existing.intervalMillis == interval && existing.networkType == network) return
        js.schedule(
            JobInfo.Builder(id, ComponentName(context, ShieldJobService::class.java))
                .setPeriodic(interval)
                .setRequiredNetworkType(network)
                .setPersisted(true)
                .build(),
        )
    }

    private fun once(context: Context, id: Int, network: Int) {
        val js = context.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(id) != null) return
        js.schedule(
            JobInfo.Builder(id, ComponentName(context, ShieldJobService::class.java))
                .setRequiredNetworkType(network)
                .setPersisted(true)
                .build(),
        )
    }

    fun run(context: Context, id: Int) {
        val shield = context.shield
        when (id) {
            OWN_LISTS -> shield.refreshLists(shield.ownLists)
            COMMUNITY_LISTS, FIRST_COMMUNITY -> shield.refreshLists(shield.communityLists)
            SELF_UPDATE -> SelfUpdater(context).checkAndInstall() // 스위치가 꺼져 있으면 아무 요청 없이 끝남
        }
    }
}

class ShieldJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "job-${params.jobId}") {
            try {
                Jobs.run(this, params.jobId)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
