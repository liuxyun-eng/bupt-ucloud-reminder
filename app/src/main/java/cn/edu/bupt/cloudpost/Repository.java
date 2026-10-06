package cn.edu.bupt.cloudpost;

import android.content.*;
import org.json.*;
import java.util.*;

final class Repository {
    static SharedPreferences prefs(Context c) {return c.getSharedPreferences("data",0);}
    static List<Models.Task> tasks(Context c) {
        List<Models.Task> list=new ArrayList<>();
        try {JSONArray a=new JSONArray(prefs(c).getString("tasks","[]"));for(int i=0;i<a.length();i++)list.add(Models.Task.from(a.getJSONObject(i)));}
        catch(JSONException ignored) {}
        return list;
    }
    static List<Models.Notice> notices(Context c) {
        List<Models.Notice> list=new ArrayList<>();
        try {JSONArray a=new JSONArray(prefs(c).getString("notices","[]"));for(int i=0;i<a.length();i++)list.add(Models.Notice.from(a.getJSONObject(i)));}
        catch(JSONException ignored) {}
        return list;
    }
    static Set<String> set(Context c,String key) {return new HashSet<>(prefs(c).getStringSet(key,Collections.emptySet()));}
    static boolean completed(Context c,Models.Task t) {return set(c,"completed").contains(t.id());}
    static void toggleCompleted(Context c,Models.Task t) {
        synchronized(SessionVault.LOCK) {
            Set<String> done=set(c,"completed");if(!done.remove(t.id()))done.add(t.id());
            prefs(c).edit().putStringSet("completed",done).commit();
            if (done.contains(t.id())) c.getSystemService(android.app.NotificationManager.class).cancel("task:"+t.id(),0);
            ReminderScheduler.schedule(c);
        }
    }
    static void saveSnapshot(Context c,List<Models.Notice> notices,List<Models.Task> tasks) throws JSONException {
        SharedPreferences p=prefs(c);
        boolean initial=p.getBoolean("initialized",false);
        long watermark=p.getLong("watermark",0);
        Set<String> seen=set(c,"seen"),pending=set(c,"pendingNotifications");
        for(Models.Notice n:notices) {
            if(Core.isNew(n,initial,seen,watermark))pending.add(n.id());
            seen.add(n.id());
        }
        long next=watermark;
        for(Models.Notice n:notices)next=Math.max(next,n.published());
        // Only IDs at the watermark need to remain for same-minute additions; older rows are excluded by time.
        Set<String> recentSeen=new HashSet<>(seen);
        Map<String,Models.Notice> cache=new LinkedHashMap<>();
        for(Models.Notice n:notices(c))cache.put(n.id(),n);
        for(Models.Notice n:notices)cache.put(n.id(),n);
        List<Models.Notice> merged=new ArrayList<>(cache.values());merged.sort(Comparator.comparingLong(Models.Notice::published).reversed());
        JSONArray ns=new JSONArray(),ts=new JSONArray();
        Set<String> retained=new HashSet<>();
        for(Models.Notice n:merged) {
            if(ns.length()<200 || pending.contains(n.id())){ns.put(n.json());retained.add(n.id());}
        }
        recentSeen.retainAll(retained);
        Set<String> taskIds=new HashSet<>();
        for(Models.Task t:tasks){ts.put(t.json());taskIds.add(t.id());}
        Set<String> done=set(c,"completed");done.retainAll(taskIds);
        Set<String> alarms=set(c,"deliveredReminders");
        Set<String> activeReminderKeys=new HashSet<>();
        for(Models.Task t:tasks)for(int stage:new int[]{24,2,0})activeReminderKeys.add(Core.reminderKey(t,stage));
        alarms.retainAll(activeReminderKeys);
        if(!p.edit().putString("notices",ns.toString()).putString("tasks",ts.toString()).putStringSet("seen",recentSeen)
            .putStringSet("pendingNotifications",pending).putStringSet("completed",done).putStringSet("deliveredReminders",alarms)
            .putLong("watermark",next).putBoolean("initialized",true).putLong("lastSync",System.currentTimeMillis())
            .putBoolean("authExpired",false).putString("status","同步成功").commit()) throw new JSONException("本机保存失败");
    }
    static void reset(Context c) {prefs(c).edit().clear().commit();}
}
