package cn.edu.bupt.cloudpost;

import android.content.Context;
import org.json.*;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class ApiClient {
    static final String HOST="https://apiucloud.bupt.edu.cn";
    static final String NEWS="/ykt-basics/api/inform/news/list";
    static final String TODO="/ykt-site/site/student/undone";
    static final String REFRESH="/ykt-basics/oauth/token";
    static final String ROLES="/ykt-basics/userroledomaindept/listByUserId";
    static final String GRANTS="/ykt-basics/menu/role-grant";
    static final String INFO="/ykt-basics/info";
    private static final Object REFRESH_LOCK=new Object();
    static final class LoginExpired extends IOException { LoginExpired(){super("学校登录已过期，请重新登录");} }
    static final class AccessDenied extends IOException { AccessDenied(){super("学校未授予当前身份访问权限，请核对学生身份");} }
    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status){super("平台返回 HTTP "+status);this.status=status;}
    }
    private final Context context;
    private SessionVault.Session session;
    ApiClient(Context context,SessionVault.Session session){this.context=context;this.session=session;}
    JSONObject get(String path,String method) throws Exception {
        try { return request(path,method,null); }
        catch (LoginExpired expired) {
            synchronized(REFRESH_LOCK) {
                SessionVault.Session current=SessionVault.load(context);
                if(current==null || current.generation()!=session.generation())throw expired;
                boolean refreshed=!current.token().equals(session.token());session=current;
                if(!refreshed) {
                    if(session.refresh().isEmpty())throw expired;
                    JSONObject token=request(REFRESH,"POST","grant_type=refresh_token&refresh_token="+ApiParser.q(session.refresh()));
                    String access=token.getString("access_token");
                    if(access.isEmpty())throw expired;
                    session=SessionVault.refresh(context,session,access,token.optString("refresh_token",session.refresh()));
                }
            }
            return request(path,method,null);
        }
    }
    private JSONObject request(String path,String method,String body) throws Exception {
        String base=path.split("\\?",2)[0];
        if (!base.equals(NEWS) && !base.equals(TODO) && !base.equals(REFRESH) && !base.equals(ROLES) && !base.equals(GRANTS) && !base.equals(INFO)) throw new IOException("不支持的请求");
        if((base.equals(ROLES) || base.equals(GRANTS) || base.equals(INFO)) && !"GET".equals(method))throw new IOException("页面初始化只允许读取学校数据");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        HttpsURLConnection connection=(HttpsURLConnection)new URL(HOST+path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);connection.setReadTimeout(20_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization",session.client().authorization());
        connection.setRequestProperty("Tenant-Id","000000");
        connection.setRequestProperty("Blade-Auth",session.token());
        if (!session.identity().isEmpty() && SchoolAuth.includeIdentity(session.client(),base))connection.setRequestProperty("identity",session.identity());
        connection.setRequestProperty("Cache-Control","no-cache");
        try {
            if ("POST".equals(method)) {
                byte[] bytes=(body==null?"":body).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
                try (OutputStream out=connection.getOutputStream()){out.write(bytes);}
            }
            int status=connection.getResponseCode();
            if(status==403)throw new AccessDenied();
            if (status==401 || (base.equals(REFRESH) && status==400)) throw new LoginExpired();
            if (status!=200) throw new HttpFailure(status);
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try (InputStream in=connection.getInputStream()) {
                byte[] bytes=new byte[8192]; int n;
                while ((n=in.read(bytes))!=-1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                    if (buffer.size()+n>4*1024*1024) throw new IOException("平台响应过大");
                    buffer.write(bytes,0,n);
                }
            }
            JSONObject response=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
            int code=response.optInt("code",200);
            if(code==403)throw new AccessDenied();
            if (code==401) throw new LoginExpired();
            return response;
        } finally {connection.disconnect();}
    }
}
