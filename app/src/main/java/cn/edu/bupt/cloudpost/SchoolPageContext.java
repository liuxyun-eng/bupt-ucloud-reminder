package cn.edu.bupt.cloudpost;

import org.json.*;
import java.net.URI;
import java.util.*;

/** Restores the same server-issued role/menu context used by the official course frontend. */
final class SchoolPageContext {
    static final class Problem extends JSONException {
        final String diagnostic;
        Problem(String diagnostic,String message){super(message);this.diagnostic=diagnostic;}
    }
    record Role(JSONObject selected,JSONArray all) {}
    static Role studentRole(JSONObject response,String identity,String preferredRoleId) throws JSONException {
        JSONArray rows=arrayData(response),roles=new JSONArray();
        List<JSONObject> candidates=new ArrayList<>(),preferred=new ArrayList<>();
        for(int i=0;i<rows.length();i++) {
            if(!(rows.opt(i) instanceof JSONObject))throw new Problem("ROLE_ITEM_FORMAT","学校身份列表格式异常");
            JSONObject source=rows.getJSONObject(i),role=new JSONObject();
            for(String key:new String[]{"id","roleId","roleAliase","roleName","dept"}) {
                if(source.has(key) && (!source.isNull(key) || key.equals("dept")))role.put(key,key.equals("id") || key.equals("roleId")?exactId(source.get(key)):source.get(key));
            }
            roles.put(role);
            if("JS005".equals(role.optString("roleAliase")) && identity.equals("JS005:"+frontendDept(source))) {
                candidates.add(role);
                if(!preferredRoleId.isEmpty() && preferredRoleId.equals(role.optString("roleId")))preferred.add(role);
            }
        }
        if(!preferred.isEmpty())candidates=preferred;
        if(candidates.isEmpty())throw new Problem("ROLE_NO_MATCH","找不到当前登录的学生身份，请重新连接账号");
        JSONObject selected=candidates.get(0);
        if(!selected.has("roleId"))throw new Problem("ROLE_ID_MISSING","学校学生身份缺少角色编号");
        String roleId=exactId(selected.get("roleId"));
        for(JSONObject candidate:candidates)if(!roleId.equals(candidate.optString("roleId")))
            throw new Problem("ROLE_AMBIGUOUS","学生身份不唯一，请重新连接学校账号");
        return new Role(selected,roles);
    }
    static List<String> grants(JSONObject response) throws JSONException {
        JSONArray rows=arrayData(response);Set<String> codes=new LinkedHashSet<>();
        for(int i=0;i<rows.length();i++) {
            if(!(rows.opt(i) instanceof JSONObject))throw new Problem("GRANT_ITEM_FORMAT","学校权限列表格式异常");
            JSONObject item=rows.getJSONObject(i);
            if(!item.has("code") || item.isNull("code"))continue;
            Object value=item.get("code");
            if(!(value instanceof String))throw new Problem("GRANT_CODE_TYPE","学校权限列表格式异常");
            String code=(String)value;
            if(code.isEmpty())continue;
            if(code.contains(",") || code.indexOf('\n')>=0 || code.indexOf('\r')>=0)throw new Problem("GRANT_CODE_FORMAT","学校权限编号格式异常");
            codes.add(code);
        }
        return new ArrayList<>(codes);
    }
    static JSONObject userInfo(JSONObject response,String userId) throws JSONException {
        requireSuccessfulResponse(response);
        if(!(response.opt("data") instanceof JSONObject))throw new Problem("USER_DATA_FORMAT","学校账号信息格式异常");
        JSONObject data=response.getJSONObject("data");
        if(!data.has("id"))throw new Problem("USER_ID_MISSING","学校账号信息缺少账号编号");
        if(!userId.equals(exactId(data.get("id"))))throw new Problem("USER_ID_MISMATCH","学校返回的账号不一致，请重新连接账号");
        JSONObject result=new JSONObject();
        for(String key:new String[]{"id","account","realName","avatar","email"})
            if(data.has(key) && !data.isNull(key))result.put(key,key.equals("id")?exactId(data.get(key)):data.get(key));
        return result;
    }
    static String requiredCode(String destination) {
        try {
            URI u=new URI(destination);
            if(!LoginNavigation.schoolUrl(destination) || !"ucloud.bupt.edu.cn".equalsIgnoreCase(u.getHost()))return "";
            if(!"/uclass/course.html".equals(u.getPath()) || u.getFragment()==null)return "";
            String route=u.getFragment().split("\\?",2)[0];
            return switch(route) {
                case "/student/assignmentDetails_fullpage"->"stuAssignmentInfo";
                case "/student/assignmentEvaluation_fullpage"->"assignmentEvaluation";
                case "/testing-details"->"stuExamInfo";
                default->"";
            };
        } catch(Exception invalid){return "";}
    }
    static void requirePageGrant(String destination,List<String> codes) {
        String code=requiredCode(destination);
        if(!code.isEmpty() && !codes.contains(code))throw new IllegalStateException("学校返回的学生权限中没有该页面，请在网页核对当前身份");
    }
    static String storageScript(Role role,List<String> codes,SchoolAuth.Client client) {
        String permissions=JSONObject.quote(JSONObject.quote(String.join(",",codes)));
        String roles=JSONObject.quote(role.all().toString());
        return "(function(){try{if(window.origin!=='https://ucloud.bupt.edu.cn')return 'STORAGE_ORIGIN';localStorage.setItem('user-role-permission',"+permissions+");"+
            "localStorage.setItem('login-roles',"+roles+");"+
            (client==SchoolAuth.Client.PORTAL?"sessionStorage.setItem('ykt_login_from','fromPortal');":"sessionStorage.removeItem('ykt_login_from');")+
            "if(localStorage.getItem('user-role-permission')!=="+permissions+" || localStorage.getItem('login-roles')!=="+roles+
            (client==SchoolAuth.Client.PORTAL?" || sessionStorage.getItem('ykt_login_from')!=='fromPortal'":" || sessionStorage.getItem('ykt_login_from')!==null")+
            ")return 'STORAGE_VERIFY';return 'STORAGE_OK';}catch(e){return 'STORAGE_WRITE';}})()";
    }
    static String storageResult(String result) {
        try {
            String code=new JSONArray("["+result+"]").getString(0);
            if(Arrays.asList("STORAGE_OK","STORAGE_ORIGIN","STORAGE_VERIFY","STORAGE_WRITE").contains(code))return code;
        } catch(Exception ignored){}
        return "STORAGE_RESULT_INVALID";
    }
    private static JSONArray arrayData(JSONObject response) throws JSONException {
        requireSuccessfulResponse(response);
        if(!(response.opt("data") instanceof JSONArray))throw new Problem("RESPONSE_ARRAY_FORMAT","学校身份或权限列表格式异常");
        return response.getJSONArray("data");
    }
    private static void requireSuccessfulResponse(JSONObject response) throws Problem {
        if(response.has("code") && response.optInt("code",-1)!=200)throw new Problem("RESPONSE_CODE_"+response.optInt("code",-1),"学校返回身份或权限查询失败，请重试");
        if(response.has("success") && !response.optBoolean("success"))throw new Problem("RESPONSE_REJECTED","学校未完成身份或权限查询，请重试");
    }
    private static String frontendDept(JSONObject source) {
        // The official JavaScript uses role.roleAliase + ':' + role.dept, including null/undefined values.
        if(!source.has("dept"))return "undefined";
        Object dept=source.opt("dept");return dept==null || dept==JSONObject.NULL?"null":String.valueOf(dept);
    }
    private static String exactId(Object value) throws JSONException {
        if(!(value instanceof String || value instanceof Long || value instanceof Integer) || value.toString().trim().isEmpty())
            throw new Problem("IDENTIFIER_FORMAT","学校身份编号格式异常");
        return value.toString();
    }
}
