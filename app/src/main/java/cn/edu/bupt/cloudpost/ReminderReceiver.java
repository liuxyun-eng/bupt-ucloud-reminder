package cn.edu.bupt.cloudpost;
import android.content.*;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){Notifier.channels(c);ReminderScheduler.receive(c,i.getStringExtra("key"));}
}
