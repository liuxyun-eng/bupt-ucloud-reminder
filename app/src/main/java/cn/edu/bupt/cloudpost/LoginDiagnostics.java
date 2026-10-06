package cn.edu.bupt.cloudpost;

import java.net.URI;

/** Stores only predefined stage labels and numeric failures. No URL, account or token is retained. */
final class LoginDiagnostics {
    private String stage="打开教学云主入口",failure="无";
    void page(String url) {
        if(LoginNavigation.portalPage(url))stage=LoginNavigation.hasTicket(url)?"门户登录凭证交换":"教学云主入口";
        else if(LoginNavigation.studentHome(url))stage="学生课程主页";
        else if(LoginNavigation.schoolUrl(url)) {
            try {if("auth.bupt.edu.cn".equalsIgnoreCase(new URI(url).getHost()))stage="学校统一身份认证";}
            catch(Exception ignored){}
        }
    }
    void preparing(){stage="学生页面权限初始化";failure="无";}
    void prepared(){stage="学校详情页面";failure="无";}
    void preparationStep(PageInitStep step){stage="学生页面权限初始化 · "+step.label;failure="无";}
    void preparationFailed(PageInitStep step,String code){stage="学生页面权限初始化 · "+step.label;failure=code;}
    void ready(){stage="学生身份登录完成";failure="无";}
    void ticketReplay(){failure="重复登录回调已拦截";}
    void blocked(){failure="非学校 HTTPS 页面已拦截";}
    void ssl(){failure="学校页面证书校验失败";}
    void network(int code){failure="页面加载错误 "+code;}
    void http(String url,int status) {
        if(!LoginNavigation.schoolUrl(url))return;
        try {
            URI u=new URI(url);
            if("apiucloud.bupt.edu.cn".equalsIgnoreCase(u.getHost()) && "/ykt-basics/oauth/token".equals(u.getPath()))
                failure="学校登录凭证接口 HTTP "+status;
        } catch(Exception ignored){}
    }
    String report(String androidVersion,String webViewVersion){return "云邮提醒 0.1.4\nAndroid："+androidVersion+"\nWebView："+webViewVersion+"\n步骤："+stage+"\n错误："+failure;}
}
