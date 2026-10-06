package cn.edu.bupt.cloudpost;

import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ApiParser {
    static JSONObject data(JSONObject response) throws JSONException {
        if (response.optInt("code",-1)!=200 || !(response.opt("data") instanceof JSONObject))
            throw new JSONException("平台响应格式或权限发生变化");
        return response.getJSONObject("data");
    }
    static List<Models.Notice> notices(JSONObject data) throws JSONException {
        JSONArray rows=data.getJSONArray("records");
        List<Models.Notice> result=new ArrayList<>();
        for (int i=0;i<rows.length();i++) {
            JSONObject o=rows.getJSONObject(i);
            String id=id(o.get("id"));
            long published=Models.time(o.get("newsCopyTime"));
            if (id.trim().isEmpty() || published<=0) throw new JSONException("通知编号或时间缺失");
            result.add(new Models.Notice(id,o.getString("newsTitle"),o.optString("newsInfo"),published,o.optInt("isRead",1)==0));
        }
        return result;
    }
    static List<Models.Task> tasks(JSONObject data) throws JSONException {
        JSONArray rows=data.getJSONArray("undoneList");
        if (data.has("undoneNum") && data.getInt("undoneNum")!=rows.length())
            throw new JSONException("待办列表不完整，已保留上次结果");
        List<Models.Task> result=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for (int i=0;i<rows.length();i++) {
            JSONObject o=rows.getJSONObject(i);
            int type=o.getInt("type");
            String activity=id(o.get("activityId"));
            String id=type+":"+activity+":"+o.optString("studentGroupId","");
            if (activity.trim().isEmpty() || !ids.add(id)) throw new JSONException("待办编号缺失或重复");
            String title=o.getString("activityName");
            String kind=switch(type) { case 1->"讨论"; case 2->"问卷"; case 3->"作业"; case 4->"测验"; case 5->"互评"; default->"待办"; };
            String url=Models.HOME;
            if (type==3 || type==5) {
                url="https://ucloud.bupt.edu.cn/uclass/course.html#/student/assignmentDetails_fullpage?activeTabName="+(type==3?"first":"fourth")+
                    "&assignmentId="+q(activity)+"&assignmentType="+q(o.optString("assignmentType"))+
                    "&assignmentTitle="+q(title)+"&evaluationStatus="+q(o.optString("evaluationStatus"))+
                    "&studentGroupId="+q(o.optString("studentGroupId"))+"&isOpenEvaluation="+q(o.optString("isOpenEvaluation"));
            } else if (type==4) url="https://ucloud.bupt.edu.cn/uclass/course.html#/answer?id="+q(activity);
            // Opening a discussion/questionnaire via the official homepage preserves the platform's routing context.
            result.add(new Models.Task(id,title,o.optString("siteName",""),kind,Models.time(o.opt("endTime")),url));
        }
        result.sort(Comparator.comparingLong(t->t.due()==0?Long.MAX_VALUE:t.due()));
        return result;
    }
    static String q(String s) { try { return URLEncoder.encode(s,"UTF-8"); } catch(java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); } }
    private static String id(Object value) throws JSONException {
        if(value instanceof String || value instanceof Long || value instanceof Integer)return value.toString();
        throw new JSONException("无法精确读取平台编号");
    }
}
