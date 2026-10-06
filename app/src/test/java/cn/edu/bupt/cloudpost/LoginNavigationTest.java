package cn.edu.bupt.cloudpost;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;
import java.net.URLDecoder;

public final class LoginNavigationTest {
    @Test public void portalServiceAndClientMatchCurrentSchoolFrontend() throws Exception {
        String query=new URI(LoginNavigation.AUTH_LOGIN).getRawQuery();
        assertEquals("https://ucloud.bupt.edu.cn",URLDecoder.decode(query.substring("service=".length()),"UTF-8"));
        assertEquals("portal:portal_secret",new String(java.util.Base64.getDecoder().decode(SchoolAuth.Client.PORTAL.authorization().substring(6)),java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void fragmentlessPortalCallbackRetainsCompleteTicket() throws Exception {
        LoginNavigation.Decision d=new LoginNavigation().decide("https://ucloud.bupt.edu.cn/?ticket=ST-example-123",true);
        assertEquals(LoginNavigation.Action.LOAD_TOP,d.action());
        assertEquals("https://ucloud.bupt.edu.cn/?ticket=ST-example-123#/",d.destination());
        String value=URLDecoder.decode(d.destination().split("ticket=",2)[1],"UTF-8");
        assertEquals("ST-example-123",value.substring(0,value.lastIndexOf('#')));
    }
    @Test public void oldCourseTicketIsNeverReusedWithPortalService(){
        for(String url:new String[]{"http://ucloud.bupt.edu.cn/uclass?ticket=ST-old","https://ucloud.bupt.edu.cn/uclass/?ticket=ST-old#/index","http://ucloud.bupt.edu.cn/?ticket=ST-http"}) {
            LoginNavigation.Decision d=new LoginNavigation().decide(url,true);
            assertEquals(LoginNavigation.Action.LOAD_TOP,d.action());assertEquals(LoginNavigation.AUTH_LOGIN,d.destination());
            assertFalse(d.destination().contains("ST-old"));
        }
    }
    @Test public void obsoleteLoginRedirectRequestsFreshPortalTicket(){
        LoginNavigation.Decision d=new LoginNavigation().decide("https://auth.bupt.edu.cn/authserver/login?service=http%3A%2F%2Fucloud.bupt.edu.cn%2Fuclass",true);
        assertEquals(LoginNavigation.AUTH_LOGIN,d.destination());
    }
    @Test public void duplicateCallbackNeedsFreshCredential(){
        LoginNavigation nav=new LoginNavigation();String url="https://ucloud.bupt.edu.cn/?ticket=ST-once";
        assertEquals(LoginNavigation.Action.LOAD_TOP,nav.decide(url,false).action());
        assertEquals(LoginNavigation.Action.REUSED_TICKET,nav.decide(url,false).action());
        assertEquals(LoginNavigation.Action.LOAD_TOP,nav.decide(url.replace("ST-once","ST-fresh"),false).action());
    }
    @Test public void safeNormalNavigationIsAllowedWithoutReload(){
        for(String url:new String[]{LoginNavigation.AUTH_LOGIN,LoginNavigation.PORTAL_HOME,Models.HOME,"https://auth.bupt.edu.cn/authserver/login"})
            assertEquals(LoginNavigation.Action.ALLOW,new LoginNavigation().decide(url,true).action());
    }
    @Test public void unrelatedHttpIframeNeverReplacesMainPage(){
        assertEquals(LoginNavigation.Action.BLOCK,new LoginNavigation().decide("http://ucloud.bupt.edu.cn/some-frame",false).action());
    }
    @Test public void duplicateCredentialsAndHostSpoofingAreBlocked(){
        assertEquals(LoginNavigation.Action.BLOCK,new LoginNavigation().decide("https://ucloud.bupt.edu.cn/?ticket=ST-a&ticket=ST-b",true).action());
        assertEquals(LoginNavigation.Action.BLOCK,new LoginNavigation().decide("https://ucloud.bupt.edu.cn.evil.example/?ticket=ST-a",true).action());
        assertFalse(LoginNavigation.schoolUrl("https://auth.bupt.edu.cn@evil.example/login"));
        assertFalse(LoginNavigation.schoolUrl("https://auth.bupt.edu.cn:444/login"));
        assertEquals(LoginNavigation.Action.BLOCK,new LoginNavigation().decide("https://ucloud.bupt.edu.cn:444/?ticket=ST-a",true).action());
    }
    @Test public void callbackQueryOrderingStillFitsLegacyParser() throws Exception {
        LoginNavigation.Decision d=new LoginNavigation().decide("https://ucloud.bupt.edu.cn/?ticket=ST%2Dencoded&from=school%26portal",true);
        assertEquals("https://ucloud.bupt.edu.cn/?from=school%26portal&ticket=ST%2Dencoded#/",d.destination());
        String value=URLDecoder.decode(d.destination().split("ticket=",2)[1],"UTF-8");
        assertEquals("ST-encoded",value.substring(0,value.lastIndexOf('#')));
    }
    @Test public void canonicalCallbackLoadsOnce(){
        LoginNavigation nav=new LoginNavigation();String url="https://ucloud.bupt.edu.cn/?ticket=ST-canonical#/";
        assertEquals(LoginNavigation.Action.ALLOW,nav.decide(url,true).action());
        assertEquals(LoginNavigation.Action.REUSED_TICKET,nav.decide(url,true).action());
    }
    @Test public void connectAcceptsCleanPortalOrStudentHome(){
        assertTrue(LoginNavigation.canConnect(LoginNavigation.PORTAL_HOME));
        assertTrue(LoginNavigation.canConnect(Models.HOME));
        assertFalse(LoginNavigation.canConnect(Models.LOGIN));
        assertFalse(LoginNavigation.canConnect(LoginNavigation.AUTH_LOGIN));
        assertFalse(LoginNavigation.canConnect("https://ucloud.bupt.edu.cn/?ticket=ST-used#/"));
        assertFalse(LoginNavigation.canConnect("https://ucloud.bupt.edu.cn/uclass/?token=example#/student/homePage"));
        assertFalse(LoginNavigation.canConnect("https://ucloud.bupt.edu.cn/uclass/#/teacher/homePage"));
        assertFalse(LoginNavigation.canConnect("https://evil.example/#/"));
    }
}
