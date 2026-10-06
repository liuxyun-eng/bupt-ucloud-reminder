package cn.edu.bupt.cloudpost;

import java.util.*;

/** Pure scheduling and delta rules, shared by sync, alarms and tests. */
final class Core {
    static final long HOUR=3_600_000L;
    static int currentStage(long due, long now) {
        if (due<=0) return -1;
        long remaining=due-now;
        return remaining<=0 ? 0 : remaining<=2*HOUR ? 2 : remaining<=24*HOUR ? 24 : -1;
    }
    static String reminderKey(Models.Task task, int stage) { return task.id()+":"+task.due()+":"+stage; }
    static boolean isNew(Models.Notice n, boolean initialized, Set<String> seen, long watermark) {
        return initialized && !seen.contains(n.id()) && n.published()>=watermark;
    }
    static String countdown(long due, long now) {
        if (due<=0) return "无截止时间";
        long t=due-now;
        if (t<=0) return "已截止";
        long minutes=(t+59_999)/60_000;
        if (minutes>=1440) return "还剩 "+(minutes/1440)+" 天 "+((minutes%1440)/60)+" 小时";
        if (minutes>=60) return "还剩 "+(minutes/60)+" 小时 "+(minutes%60)+" 分钟";
        return "还剩 "+minutes+" 分钟";
    }
}
