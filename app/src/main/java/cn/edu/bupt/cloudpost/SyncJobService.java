package cn.edu.bupt.cloudpost;

import android.app.job.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Future;

public final class SyncJobService extends JobService {
    static final int JOB_ID=701;
    private Future<?> task;
    private JobParameters active;
    static void schedule(Context c) {
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        try {
            if(SessionVault.load(c)==null || !Repository.prefs(c).getBoolean("autoSync",true)){scheduler.cancel(JOB_ID);return;}
        } catch(Exception e){scheduler.cancel(JOB_ID);return;}
        int minutes=Repository.prefs(c).getInt("interval",15);
        JobInfo desired=new JobInfo.Builder(JOB_ID,new ComponentName(c,SyncJobService.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(minutes*60_000L).setPersisted(true).build();
        JobInfo existing=scheduler.getPendingJob(JOB_ID);
        if(existing==null || existing.getIntervalMillis()!=desired.getIntervalMillis())scheduler.schedule(desired);
    }
    @Override public boolean onStartJob(JobParameters params) {
        active=params;
        task=SyncRunner.start(getApplicationContext(),status->new Handler(Looper.getMainLooper()).post(()->{
            if(active==params){jobFinished(params,false);active=null;}
        }));
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){active=null;if(task!=null)task.cancel(true);return false;}
}
