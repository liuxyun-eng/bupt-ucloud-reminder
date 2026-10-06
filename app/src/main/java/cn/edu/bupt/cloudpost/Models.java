package cn.edu.bupt.cloudpost;

import org.json.*;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

final class Models {
    static final String HOME = "https://ucloud.bupt.edu.cn/uclass/index.html#/student/homePage";
    static final String NOTICES = "https://ucloud.bupt.edu.cn/uclass/index.html#/set/notice_fullpage";
    static final String LOGIN = "https://ucloud.bupt.edu.cn/uclass/index.html#/index";
    static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");
    static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MM月dd日 HH:mm").withZone(SCHOOL_ZONE);

    record Notice(String id, String title, String body, long published, boolean unread) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("title",title).put("body",body).put("published",published).put("unread",unread);
        }
        static Notice from(JSONObject o) throws JSONException {
            return new Notice(o.getString("id"),o.getString("title"),o.getString("body"),o.getLong("published"),o.optBoolean("unread"));
        }
    }
    record Task(String id, String title, String course, String kind, long due, String url) {
        JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("title",title).put("course",course).put("kind",kind).put("due",due).put("url",url);
        }
        static Task from(JSONObject o) throws JSONException {
            return new Task(o.getString("id"),o.getString("title"),o.optString("course"),o.optString("kind","作业"),o.getLong("due"),o.optString("url",HOME));
        }
    }
    static long time(Object value) {
        if (value == null || value == JSONObject.NULL || String.valueOf(value).trim().isEmpty()) return 0;
        if (value instanceof Number) {
            long t = ((Number)value).longValue();
            return t > 0 && t < 100_000_000_000L ? t*1000 : t;
        }
        String s=String.valueOf(value).trim();
        try { return Instant.parse(s).toEpochMilli(); } catch (RuntimeException ignored) {}
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli(); } catch (RuntimeException ignored) {}
        for (String f: Arrays.asList("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS")) {
            try { return LocalDateTime.parse(s,DateTimeFormatter.ofPattern(f)).atZone(SCHOOL_ZONE).toInstant().toEpochMilli(); }
            catch (RuntimeException ignored) {}
        }
        throw new IllegalArgumentException("平台时间格式发生变化");
    }
    static String date(long time) { return time == 0 ? "未设置截止时间" : FORMAT.format(Instant.ofEpochMilli(time)); }
}
