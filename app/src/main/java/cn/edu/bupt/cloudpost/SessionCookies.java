package cn.edu.bupt.cloudpost;

import org.json.JSONObject;
import java.net.URLDecoder;
import java.util.*;

/** Only the five cookies required for the student session are read. */
final class SessionCookies {
    record Credentials(String token,String refresh,String userId,String identity,String roleId) {}
    static Credentials read(String header) throws Exception {
        if(header==null)throw new IllegalStateException("请先完成学校登录");
        Map<String,String> cookies=new HashMap<>();
        Set<String> allowed=new HashSet<>(Arrays.asList("iClass-token","iClass-refresh_token","iClass-uuid","iClass-identity","iClass-user-role"));
        for(String item:header.split(";")) {
            int eq=item.indexOf('=');if(eq<0)continue;
            String name=item.substring(0,eq).trim();
            if(allowed.contains(name)) {
                String value=URLDecoder.decode(item.substring(eq+1).trim().replace("+","%2B"),"UTF-8");
                String previous=cookies.put(name,value);
                if(previous!=null && !previous.equals(value))throw new IllegalStateException("学校登录缓存有冲突，请点击「重新登录」");
            }
        }
        String token=cookies.getOrDefault("iClass-token",""),refresh=cookies.getOrDefault("iClass-refresh_token","");
        String uid=cookies.getOrDefault("iClass-uuid",""),identity=cookies.getOrDefault("iClass-identity","");
        if(token.isEmpty() || refresh.isEmpty() || uid.isEmpty())throw new IllegalStateException("学校尚未完成登录，请稍候");
        JSONObject role=new JSONObject(cookies.getOrDefault("iClass-user-role","{}"));
        if(!"JS005".equals(role.optString("roleAliase")) || !identity.startsWith("JS005:"))
            throw new IllegalStateException("请在教学云右上角选择学生身份，再连接提醒");
        Object roleId=role.opt("roleId");
        if(roleId!=null && roleId!=JSONObject.NULL && !(roleId instanceof String || roleId instanceof Long || roleId instanceof Integer))
            throw new IllegalStateException("学校学生身份数据异常，请重新连接账号");
        return new Credentials(token,refresh,uid,identity,roleId==null || roleId==JSONObject.NULL?"":roleId.toString());
    }
}
