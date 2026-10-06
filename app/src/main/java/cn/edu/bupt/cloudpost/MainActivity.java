package cn.edu.bupt.cloudpost;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int INK=Color.rgb(29,48,41),GREEN=Color.rgb(47,100,87),MUTED=Color.rgb(111,124,117),BG=Color.rgb(245,246,241);
    private LinearLayout content;
    private ScrollView scroller;
    private int renderedTab=-1;
    private int tab=0;
    private boolean busy=false,showCompleted=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable updated=()->handler.post(()->{if(!isDestroyed())render();});
    private final Runnable refresh=new Runnable(){public void run(){if(!isFinishing()){render();handler.postDelayed(this,30_000);}}};

    @Override public void onCreate(Bundle state){super.onCreate(state);if(state!=null){tab=state.getInt("tab");showCompleted=state.getBoolean("showCompleted");}Notifier.channels(this);openNotification(getIntent());}
    @Override public void onResume(){
        super.onResume();
        SyncRunner.OBSERVERS.add(updated);
        Notifier.flush(this);ReminderScheduler.schedule(this);SyncJobService.schedule(this);handler.post(refresh);
        try {
            if(Build.VERSION.SDK_INT>=33 && SessionVault.load(this)!=null && !Repository.prefs(this).getBoolean("notificationAsked",false)) {
                Repository.prefs(this).edit().putBoolean("notificationAsked",true).apply();
                if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
            }
        } catch(Exception ignored){}
    }
    @Override public void onPause(){handler.removeCallbacks(refresh);SyncRunner.OBSERVERS.remove(updated);super.onPause();}
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);openNotification(i);}
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("tab",tab);b.putBoolean("showCompleted",showCompleted);}
    private void openNotification(Intent i){String url=i.getStringExtra("url");if(url!=null){i.removeExtra("url");if(SchoolActivity.schoolUrl(url))SchoolActivity.open(this,url);}}

    private void render() {
        int scrollY=scroller!=null && renderedTab==tab?scroller.getScrollY():0;
        renderedTab=tab;
        LinearLayout root=column();root.setBackgroundColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(22),insets.getSystemWindowInsetTop()+dp(12),dp(22),insets.getSystemWindowInsetBottom()+dp(8));return insets;});
        root.setPadding(dp(22),dp(24),dp(22),dp(8));setContentView(root);
        TextView brand=text("CLOUD POST  /  云邮提醒",11,MUTED);brand.setLetterSpacing(.12f);root.addView(brand);space(root,8);
        LinearLayout top=row();TextView heading=text(tab==0?"每个截止，都有准备。":tab==1?"课堂动态，及时知道。":"让提醒适合你。",23,INK);heading.setTypeface(null,Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        space(root,12);
        long last=Repository.prefs(this).getLong("lastSync",0);
        String status=Repository.prefs(this).getString("status","尚未连接教学云");
        root.addView(text((last==0?"等待首次同步":"上次成功同步 "+Models.date(last))+"\n"+status,12,MUTED));space(root,14);
        LinearLayout nav=row();String[] labels={"待办","通知","设置"};
        for(int i=0;i<3;i++){final int target=i;Button b=button(labels[i],()->{tab=target;render();});b.setTextColor(tab==i?Color.WHITE:GREEN);b.setBackground(shape(tab==i?GREEN:Color.rgb(230,234,225),14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(43),1);p.setMargins(0,0,dp(7),0);nav.addView(b,p);}root.addView(nav);space(root,10);
        ScrollView scroll=new ScrollView(this);scroller=scroll;scroll.setFillViewport(true);content=column();scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0)todos();else if(tab==1)notices();else settings();
        space(root,8);root.addView(button(busy?"同步中…":"立即同步",this::sync));
        scroll.post(()->scroll.scrollTo(0,scrollY));
    }
    private void todos() {
        List<Models.Task> all=Repository.tasks(this);
        int count=0,urgent=0;long now=System.currentTimeMillis();
        for(Models.Task t:all)if(!Repository.completed(this,t)){count++;if(t.due()>0 && t.due()-now<=24*Core.HOUR)urgent++;}
        LinearLayout stats=row();stats.addView(stat(String.valueOf(count),"未完成待办"),new LinearLayout.LayoutParams(0,-2,1));stats.addView(stat(String.valueOf(urgent),"24 小时内或已截止"),new LinearLayout.LayoutParams(0,-2,1));content.addView(stats);space(content,14);
        if(Repository.prefs(this).getBoolean("authExpired",false))content.addView(text("登录已过期，以下为上次同步结果。请重新连接学校账号。",13,Color.rgb(163,75,48)));
        if(!Repository.prefs(this).getBoolean("initialized",false)) {
            empty("把待办带到手机上","登录学校教学云后，会在这里显示尚未完成的作业、测验等待办，并按截止时间排序。");content.addView(button("连接学校账号",()->SchoolActivity.open(this,Models.LOGIN)));return;
        }
        content.addView(button(showCompleted?"隐藏本机标记完成的待办":"查看本机标记完成的待办",()->{showCompleted=!showCompleted;render();}));space(content,8);
        boolean shown=false;
        for(Models.Task t:all){boolean complete=Repository.completed(this,t);if(complete && !showCompleted)continue;shown=true;
            LinearLayout card=card();card.addView(text(t.kind()+(t.course().isEmpty()?"":" · "+t.course()),11,MUTED));space(card,6);
            TextView title=text(t.title(),19,INK);title.setTypeface(null,Typeface.BOLD);card.addView(title);space(card,8);
            int color=t.due()>0 && t.due()-now<=24*Core.HOUR?Color.rgb(171,75,45):GREEN;
            card.addView(text(complete?"已在本机标记完成":Core.countdown(t.due(),now),15,complete?MUTED:color));card.addView(text(Models.date(t.due())+(t.due()>0?" 截止（北京时间）":""),12,MUTED));space(card,9);
            LinearLayout actions=row();actions.addView(button("打开作业",()->SchoolActivity.open(this,t.url())),new LinearLayout.LayoutParams(0,-2,1));actions.addView(button(complete?"恢复提醒":"标记完成",()->{Repository.toggleCompleted(this,t);render();}),new LinearLayout.LayoutParams(0,-2,1));card.addView(actions);addCard(card);
        }
        if(!shown)empty("当前没有未完成待办","完成状态以学校平台同步结果为准。本机标记完成只会停止本机提醒，不会替你提交作业。");
    }
    private void notices() {
        content.addView(text("首次同步仅建立记录；后续新增通知会发送系统提醒。",12,MUTED));space(content,12);
        List<Models.Notice> list=Repository.notices(this);
        if(list.isEmpty()){empty("通知会出现在这里","连接学校账号后，显示最近通知。首次同步不会把历史通知逐条推送。");return;}
        for(Models.Notice n:list){LinearLayout card=card();card.addView(text((n.unread()?"平台未读 · ":"")+Models.date(n.published()),11,MUTED));space(card,6);TextView title=text(n.title(),18,INK);title.setTypeface(null,Typeface.BOLD);card.addView(title);space(card,6);card.addView(text(Notifier.plain(n.body()),14,MUTED));space(card,8);card.addView(button("查看平台通知",()->SchoolActivity.open(this,Models.NOTICES)));addCard(card);}
    }
    private void settings() {
        LinearLayout account=card();account.addView(text("学校账号",18,INK));space(account,8);account.addView(text("通过学校网页登录。应用保存加密的登录令牌，用于后台读取通知与待办。密码不由应用读取。",13,MUTED));space(account,8);account.addView(button("登录 / 重新连接",()->SchoolActivity.open(this,Models.LOGIN)));addCard(account);
        LinearLayout notifications=card();notifications.addView(text("系统通知",18,INK));space(notifications,8);notifications.addView(text(Notifier.allowed(this,"new_notices") && Notifier.allowed(this,"deadlines")?"通知和截止提醒已允许":"请开启通知权限及通知类别，以接收提醒",13,MUTED));notifications.addView(button("允许通知 / 查看通知设置",this::permission));notifications.addView(button("发送一条测试提醒",()->{boolean sent=Notifier.post(this,"deadlines","test","云邮提醒测试","看到这条通知，就说明系统提醒可以显示。",Models.HOME);toast(sent?"已发送测试通知":"请先允许系统通知");}));addCard(notifications);
        LinearLayout reminders=card();reminders.addView(text("提醒方式",18,INK));space(reminders,8);
        Switch auto=new Switch(this);auto.setText("后台检查新通知与待办");auto.setChecked(Repository.prefs(this).getBoolean("autoSync",true));auto.setOnCheckedChangeListener((v,on)->{Repository.prefs(this).edit().putBoolean("autoSync",on).apply();SyncJobService.schedule(this);});reminders.addView(auto);
        space(reminders,10);int interval=Repository.prefs(this).getInt("interval",15);reminders.addView(button("检查间隔："+interval+" 分钟",()->new AlertDialog.Builder(this).setTitle("后台检查间隔").setSingleChoiceItems(new String[]{"15 分钟","30 分钟","60 分钟"},interval==15?0:interval==30?1:2,(dialog,which)->{Repository.prefs(this).edit().putInt("interval",new int[]{15,30,60}[which]).apply();SyncJobService.schedule(this);dialog.dismiss();render();}).show()));
        Switch deadline=new Switch(this);deadline.setText("截止前 24 小时、2 小时及截止时提醒");deadline.setChecked(Repository.prefs(this).getBoolean("deadlineEnabled",true));deadline.setOnCheckedChangeListener((v,on)->{Repository.prefs(this).edit().putBoolean("deadlineEnabled",on).apply();ReminderScheduler.schedule(this);});reminders.addView(deadline);space(reminders,10);
        reminders.addView(text("安卓会根据网络与省电状态安排后台检查，实际可能晚于设定间隔。截止提醒使用系统闹钟，也可能因省电设置延迟。",12,MUTED));addCard(reminders);
        LinearLayout privacy=card();privacy.addView(text("数据与电池",18,INK));space(privacy,8);privacy.addView(text("数据只保存在这台手机；应用直接连接学校平台。若手机限制后台运行，可在系统设置中允许本应用后台活动。",13,MUTED));privacy.addView(button("打开系统应用设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))));privacy.addView(button("断开账号并清除本机数据",this::disconnect));addCard(privacy);
        content.addView(text("云邮提醒 0.1.4 · 测试版\n接口依据学校公开前端核对，仍需手机登录与后台运行验证。",11,MUTED));
    }
    private void sync() {
        if(busy)return;
        try{if(SessionVault.load(this)==null){SchoolActivity.open(this,Models.LOGIN);return;}}
        catch(Exception e){toast("请重新连接学校账号");return;}
        busy=true;render();
        SyncRunner.start(getApplicationContext(),status->handler.post(()->{busy=false;if(!isDestroyed()){toast(status);render();}}));
    }
    private void permission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] results){super.onRequestPermissionsResult(code,p,results);Notifier.flush(this);ReminderScheduler.schedule(this);render();}
    private void disconnect() {
        new AlertDialog.Builder(this).setTitle("断开学校账号？").setMessage("清除这台手机的登录状态、缓存和提醒。之后可以重新登录，学校平台上的内容不会被删除。").setNegativeButton("取消",null).setPositiveButton("断开",(d,w)->{
            synchronized(SessionVault.LOCK){SessionVault.clear(this);ReminderScheduler.cancelAll(this);getSystemService(android.app.job.JobScheduler.class).cancelAll();getSystemService(NotificationManager.class).cancelAll();Repository.reset(this);}
            android.webkit.CookieManager.getInstance().removeAllCookies(value->{android.webkit.CookieManager.getInstance().flush();});android.webkit.WebStorage.getInstance().deleteAllData();render();
        }).show();
    }
    private LinearLayout stat(String number,String label){LinearLayout v=column();v.setPadding(dp(12),dp(12),dp(8),dp(12));TextView t=text(number,36,GREEN);t.setTypeface(null,Typeface.BOLD);v.addView(t);v.addView(text(label,11,MUTED));return v;}
    private void empty(String title,String body){LinearLayout v=card();space(v,20);v.addView(text(title,20,INK));space(v,12);v.addView(text(body,14,MUTED));space(v,20);addCard(v);}
    private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(18),dp(17),dp(18),dp(17));v.setBackground(shape(Color.WHITE,18));return v;}
    private void addCard(LinearLayout v){content.addView(v,new LinearLayout.LayoutParams(-1,-2));space(content,11);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(4),1f);return v;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setTextColor(GREEN);b.setAllCaps(false);b.setMinHeight(dp(46));b.setMinimumHeight(dp(46));b.setOnClickListener(v->action.run());return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void space(LinearLayout v,int height){View s=new View(this);v.addView(s,new LinearLayout.LayoutParams(1,dp(height)));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
