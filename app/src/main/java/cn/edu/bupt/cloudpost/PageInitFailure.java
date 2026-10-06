package cn.edu.bupt.cloudpost;

import org.json.JSONException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** Reports predefined reasons, never exception text from a response or a URL. */
record PageInitFailure(String code,String message) {
    static PageInitFailure from(Exception error) {
        if(error instanceof SchoolPageContext.Problem p)return new PageInitFailure(p.diagnostic,p.getMessage());
        if(error instanceof ApiClient.LoginExpired)return new PageInitFailure("LOGIN_EXPIRED","学校登录已过期，请重新登录");
        if(error instanceof ApiClient.AccessDenied)return new PageInitFailure("ACCESS_DENIED","学校未授予当前身份访问权限，请核对学生身份");
        if(error instanceof ApiClient.HttpFailure h)return new PageInitFailure("HTTP_"+h.status,"学校接口暂时不可用，请稍后重试");
        if(error instanceof JSONException)return new PageInitFailure("JSON_FORMAT","学校返回的数据格式异常，请复制登录诊断");
        if(error instanceof SocketTimeoutException)return new PageInitFailure("NETWORK_TIMEOUT","学校接口响应超时，请重试");
        if(error instanceof UnknownHostException)return new PageInitFailure("DNS_FAILURE","无法连接学校接口，请检查网络");
        if(error instanceof SSLException)return new PageInitFailure("TLS_FAILURE","学校接口证书校验失败，请稍后重试");
        if(error instanceof IOException)return new PageInitFailure("NETWORK_FAILURE","加载学校页面失败，请检查网络后重试");
        return new PageInitFailure("LOCAL_STATE_FAILURE","本机登录状态未能完成初始化，请重新连接账号");
    }
}
