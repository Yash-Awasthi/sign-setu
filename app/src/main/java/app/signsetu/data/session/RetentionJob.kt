package app.signsetu.data.session

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import app.signsetu.SignSetuApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** The hourly retention sweep, run by the platform scheduler even when the app is never opened again, so records on a
 * lost or seized phone still go once they are past retention. */
class RetentionJob : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as SignSetuApp).container
        work = MainScope().launch {
            container.sweepRetention().join()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }

    companion object {
        private const val ID = 4201

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(ID, ComponentName(context, RetentionJob::class.java))
                    .setPeriodic(TimeUnit.HOURS.toMillis(1))
                    // Survives a restart; needs RECEIVE_BOOT_COMPLETED, a normal permission.
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
