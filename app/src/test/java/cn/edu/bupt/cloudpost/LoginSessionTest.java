package cn.edu.bupt.cloudpost;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URLEncoder;

public final class LoginSessionTest {
    private static String cookies(String role,String identity) throws Exception {
        return "iClass-token=a%2Bb+c; iClass-refresh_token=refresh; iClass-uuid=1234567890123456789; iClass-identity="+URLEncoder.encode(identity,"UTF-8")+"; iClass-user-role="+URLEncoder.encode("{\"roleAliase\":\""+role+"\"}","UTF-8");
    }
    @Test public void studentCookiesPreserveLongIdAndPlusSigns() throws Exception {
        SessionCookies.Credentials c=SessionCookies.read(cookies("JS005","JS005:school"));
        assertEquals("a+b+c",c.token());assertEquals("refresh",c.refresh());
        assertEquals("1234567890123456789",c.userId());assertEquals("JS005:school",c.identity());
    }
    @Test public void noPasswordOrUnrelatedCookieIsDecoded() throws Exception {
        assertEquals("a+b+c",SessionCookies.read(cookies("JS005","JS005:school")+"; iClass-account=not%valid").token());
    }
    @Test public void teacherAndIncompleteRoleTransitionsCannotConnect() throws Exception {
        for(String[] pair:new String[][]{{"JS004","JS004:school"},{"JS005","JS004:school"},{"JS004","JS005:school"}}) {
            try {SessionCookies.read(cookies(pair[0],pair[1]));fail("Non-student session accepted");}
            catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("学生身份"));}
        }
    }
    @Test public void missingTokenRefreshOrUserIdCannotConnect() throws Exception {
        String complete=cookies("JS005","JS005:school");
        for(String name:new String[]{"token","refresh_token","uuid"}) {
            String incomplete=complete.replaceAll("iClass-"+name+"=[^;]*;?","");
            try {SessionCookies.read(incomplete);fail("Incomplete session accepted");}catch(IllegalStateException expected){}
        }
    }
    @Test public void conflictingOldCookiePathsRequireCleanLogin() throws Exception {
        try {SessionCookies.read(cookies("JS005","JS005:school")+"; iClass-token=stale");fail("Conflict accepted");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("缓存有冲突"));}
    }
    @Test public void portalClientSurvivesPersistenceAndLegacyDefaultsRemainCourse(){
        assertEquals(SchoolAuth.Client.PORTAL,SchoolAuth.Client.fromSaved(SchoolAuth.Client.PORTAL.value()));
        assertEquals(SchoolAuth.Client.COURSE,SchoolAuth.Client.fromSaved("course"));
        assertNotEquals(SchoolAuth.Client.COURSE.authorization(),SchoolAuth.Client.PORTAL.authorization());
        try {SchoolAuth.Client.fromSaved("other");fail("Unknown client accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void diagnosticsNeverContainCallbackCredentialOrUserId(){
        LoginDiagnostics d=new LoginDiagnostics();
        d.page("https://ucloud.bupt.edu.cn/?ticket=ST-secret#/student/homePage?user=123456");
        d.http("https://apiucloud.bupt.edu.cn/ykt-basics/oauth/token?refresh_token=secret-refresh",400);
        String report=d.report("15","123");
        assertTrue(report.contains("门户登录凭证交换"));assertTrue(report.contains("HTTP 400"));
        assertFalse(report.contains("ST-secret"));assertFalse(report.contains("secret-refresh"));assertFalse(report.contains("123456"));assertFalse(report.contains("https://"));
    }
    @Test public void readyDiagnosticsResetResolvedFailure(){
        LoginDiagnostics d=new LoginDiagnostics();d.ssl();d.ready();
        assertTrue(d.report("15","123").contains("错误：无"));
    }
    @Test public void portalRoleQueriesMatchOfficialHeadersWhileCourseQueriesKeepIdentity(){
        for(String endpoint:new String[]{"/ykt-basics/userroledomaindept/listByUserId","/ykt-basics/menu/role-grant","/ykt-basics/info"}) {
            assertFalse(SchoolAuth.includeIdentity(SchoolAuth.Client.PORTAL,endpoint));
            assertTrue(SchoolAuth.includeIdentity(SchoolAuth.Client.COURSE,endpoint));
        }
        assertTrue(SchoolAuth.includeIdentity(SchoolAuth.Client.PORTAL,"/ykt-site/site/student/undone"));
        assertTrue(SchoolAuth.includeIdentity(SchoolAuth.Client.PORTAL,"/ykt-basics/api/inform/news/list"));
    }
}
