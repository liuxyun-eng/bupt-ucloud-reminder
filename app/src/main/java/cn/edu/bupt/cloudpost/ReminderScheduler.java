package cn.edu.bupt.cloudpost;

import android.app.*;
import android.content.*;
import android.net.Uri;
import java.util.*;

final class ReminderScheduler {
    private static PendingIntent alarm(Context c,String key,boolean create) {
        Intent intent=new Intent(c,ReminderReceiver.class).setData(Uri.parse("cloudpost://reminder/"+Uri.encode(key))).putExtra("key",key);
        return PendingIntent.getBroadcast(c,0,intent,(create?PendingIntent.FLAG_UPDATE_CURRENT:PendingIntent.FLAG_NO_CREATE)|PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancelAll(Context c) {
        AlarmManager manager=c.getSystemService(AlarmManager.class);
        for(String key:Repository.set(c,"scheduledAlarms")){PendingIntent p=alarm(c,key,false);if(p!=null){manager.cancel(p);p.cancel();}}
        Repository.prefs(c).edit().putStringSet("scheduledAlarms",Collections.emptySet()).commit();
    }
    static void schedule(Context c) {
        synchronized(SessionVault.LOCK) {
            cancelAll(c);
            if(!Repository.prefs(c).getBoolean("deadlineEnabled",true))return;
            long now=System.currentTimeMillis();
            Set<String> keys=new HashSet<>(),delivered=Repository.set(c,"deliveredReminders");
            for(Models.Task task:Repository.tasks(c)) {
                if(Repository.completed(c,task) || task.due()<=0)continue;
                int current=Core.currentStage(task.due(),now);
                if(current!=-1)deliver(c,task,current);
                for(int stage:new int[]{24,2,0}) {
                    long at=task.due()-stage*Core.HOUR;
                    String key=Core.reminderKey(task,stage);
                    if(at>now && !delivered.contains(key)) {
                        c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,alarm(c,key,true));
                        keys.add(key);
                    }
                }
            }
            Repository.prefs(c).edit().putStringSet("scheduledAlarms",keys).commit();
        }
    }
    static void receive(Context c,String requested) {
        synchronized(SessionVault.LOCK) {
            if(!Repository.prefs(c).getBoolean("deadlineEnabled",true))return;
            long now=System.currentTimeMillis();
            for(Models.Task t:Repository.tasks(c)) {
                for(int stage:new int[]{24,2,0}) {
                    if(Core.reminderKey(t,stage).equals(requested) && !Repository.completed(c,t)) {
                        // An alarm delayed by Doze uses the most urgent current stage, without obsolete 24h alerts.
                        int active=Core.currentStage(t.due(),now);
                        if(active!=-1)deliver(c,t,active);
                    }
                }
            }
        }
    }
    private static void deliver(Context c,Models.Task task,int stage) {
        String key=Core.reminderKey(task,stage);
        Set<String> delivered=Repository.set(c,"deliveredReminders");
        if(delivered.contains(key))return;
        String title=stage==0?"待办已到截止时间":stage==2?"待办将在 2 小时内截止":"待办将在 24 小时内截止";
        if(Notifier.post(c,"deadlines","task:"+task.id(),title,task.title()+" · "+Models.date(task.due()),task.url())) {
            delivered.add(key);
            Repository.prefs(c).edit().putStringSet("deliveredReminders",delivered).commit();
        }
    }
}
