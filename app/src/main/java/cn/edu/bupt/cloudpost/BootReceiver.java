package cn.edu.bupt.cloudpost;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        String action=i.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action))return;
        Notifier.channels(c);SyncJobService.schedule(c);ReminderScheduler.schedule(c);
    }
}
