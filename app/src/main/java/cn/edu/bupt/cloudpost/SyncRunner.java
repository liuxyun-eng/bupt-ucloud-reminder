package cn.edu.bupt.cloudpost;

import android.content.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class SyncRunner {
    static final java.util.concurrent.CopyOnWriteArrayList<Runnable> OBSERVERS=new java.util.concurrent.CopyOnWriteArrayList<>();
    static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING=new AtomicBoolean(false);
    interface Done {void complete(String status);}
    static Future<?> start(Context c,Done done) {
        if(!RUNNING.compareAndSet(false,true)){done.complete("正在同步，请稍候");return CompletableFuture.completedFuture(null);}
        FutureTask<Void> task=new FutureTask<Void>(()->{
            SessionVault.Session session=null;
            String status;
            try {
                session=SessionVault.load(c);
                if(session==null)throw new ApiClient.LoginExpired();
                ApiClient api=new ApiClient(c,session);
                boolean initialized=Repository.prefs(c).getBoolean("initialized",false);
                long watermark=Repository.prefs(c).getLong("watermark",0);
                Map<String,Models.Notice> notices=new LinkedHashMap<>();
                boolean finished=false;
                long start=System.currentTimeMillis();
                for(int page=1;page<=50;page++) {
                    JSONObject data=ApiParser.data(api.get(ApiClient.NEWS+"?newsCopyPersonId="+ApiParser.q(session.userId())+"&current="+page+"&size=10","POST"));
                    List<Models.Notice> rows=ApiParser.notices(data);
                    int total=data.getInt("total");
                    for(Models.Notice n:rows)notices.put(n.id(),n);
                    long oldest=Long.MAX_VALUE;for(Models.Notice n:rows)oldest=Math.min(oldest,n.published());
                    if(!initialized || total==0 || page*10>=total || (!rows.isEmpty() && oldest<watermark)){finished=true;break;}
                    if(rows.isEmpty() || System.currentTimeMillis()-start>90_000)throw new IllegalStateException("通知分页未读完，已保留上次同步结果");
                }
                if(!finished)throw new IllegalStateException("新通知较多，尚未完成同步");
                List<Models.Task> tasks=ApiParser.tasks(ApiParser.data(api.get(ApiClient.TODO+"?userId="+ApiParser.q(session.userId()),"GET")));
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                synchronized(SessionVault.LOCK) {
                    SessionVault.Session current=SessionVault.load(c);
                    if(current==null || current.generation()!=session.generation())throw new IllegalStateException("登录状态已改变，请再次同步");
                    Repository.saveSnapshot(c,new ArrayList<>(notices.values()),tasks);
                    Notifier.flush(c);ReminderScheduler.schedule(c);
                }
                status="同步成功";
            } catch(ApiClient.LoginExpired expired) {
                status=expired.getMessage();
                synchronized(SessionVault.LOCK) {
                    if(matches(c,session)) {
                        if(!Repository.prefs(c).getBoolean("authExpired",false) && Repository.prefs(c).getBoolean("initialized",false))
                            Notifier.post(c,"account","session","学校登录已过期","打开云邮提醒，重新登录后继续同步。",Models.LOGIN);
                        Repository.prefs(c).edit().putBoolean("authExpired",true).commit();
                    }
                }
            } catch(JSONException | IllegalArgumentException incompatible) {status="平台数据格式发生变化，已保留上次结果";}
            catch(InterruptedException | java.io.InterruptedIOException cancelled){status="同步已中断";Thread.currentThread().interrupt();}
            catch(Exception failure){status="同步失败，请检查网络或重新登录";}
            synchronized(SessionVault.LOCK) {
                if(matches(c,session))Repository.prefs(c).edit().putString("status",status).putLong("lastAttempt",System.currentTimeMillis()).commit();
            }
            for(Runnable observer:OBSERVERS)observer.run();
            done.complete(status);
            return null;
        }) {
            @Override protected void done(){RUNNING.set(false);}
        };
        EXECUTOR.execute(task);
        return task;
    }
    private static boolean matches(Context c,SessionVault.Session session) {
        try {SessionVault.Session now=SessionVault.load(c);return session==null?now==null:now!=null && now.generation()==session.generation();}
        catch(Exception e){return false;}
    }
}
