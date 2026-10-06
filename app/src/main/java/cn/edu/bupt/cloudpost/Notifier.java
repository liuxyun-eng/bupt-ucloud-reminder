package cn.edu.bupt.cloudpost;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import java.util.*;

final class Notifier {
    static void channels(Context c) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("new_notices","教学云新通知",NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel("deadlines","待办截止提醒",NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel("account","登录状态",NotificationManager.IMPORTANCE_DEFAULT));
    }
    static boolean allowed(Context c,String channel) {
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        NotificationChannel ch=nm.getNotificationChannel(channel);
        return nm.areNotificationsEnabled() && (ch==null || ch.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    static boolean post(Context c,String channel,String tag,String title,String body,String url) {
        channels(c);if(!allowed(c,channel))return false;
        Intent target=new Intent(c,MainActivity.class).setAction("OPEN_ITEM").setData(Uri.parse("cloudpost://open/"+Uri.encode(tag))).putExtra("url",url);
        PendingIntent pi=PendingIntent.getActivity(c,0,target,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setContentIntent(pi).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try {c.getSystemService(NotificationManager.class).notify(tag,0,notification);return true;}
        catch(SecurityException ignored){return false;}
    }
    static void flush(Context c) {
        synchronized(SessionVault.LOCK) {
            Set<String> pending=Repository.set(c,"pendingNotifications");
            List<Models.Notice> fresh=new ArrayList<>();
            for(Models.Notice n:Repository.notices(c))if(pending.contains(n.id()))fresh.add(n);
            if(fresh.size()>1) {
                StringBuilder body=new StringBuilder();
                for(int i=0;i<Math.min(fresh.size(),4);i++){Models.Notice n=fresh.get(i);if(i>0)body.append("\n");body.append(n.title()).append("：").append(plain(n.body()));}
                if(post(c,"new_notices","notice:batch","有 "+fresh.size()+" 条教学云新通知",body.toString(),Models.NOTICES))
                    for(Models.Notice n:fresh)pending.remove(n.id());
            } else if(fresh.size()==1) {
                Models.Notice n=fresh.get(0);
                if(post(c,"new_notices","notice:"+n.id(),n.title(),plain(n.body()),Models.NOTICES))pending.remove(n.id());
            }
            Repository.prefs(c).edit().putStringSet("pendingNotifications",pending).commit();
        }
    }
    static String plain(String html){return Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString().trim();}
}
