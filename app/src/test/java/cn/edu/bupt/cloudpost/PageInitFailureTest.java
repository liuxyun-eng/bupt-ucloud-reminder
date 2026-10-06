package cn.edu.bupt.cloudpost;

import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.SocketTimeoutException;

public final class PageInitFailureTest {
    @Test public void parserFailureSurvivesIntoCopiedDiagnostics() {
        PageInitFailure failure=PageInitFailure.from(new SchoolPageContext.Problem("ROLE_NO_MATCH","找不到当前登录的学生身份"));
        LoginDiagnostics diagnostic=new LoginDiagnostics();
        diagnostic.preparationFailed(PageInitStep.MATCH_ROLE,failure.code());
        String report=diagnostic.report("16","151.0.7922.199");
        assertTrue(report.contains("匹配学生身份"));assertTrue(report.contains("错误：ROLE_NO_MATCH"));
        diagnostic.preparationStep(PageInitStep.GRANTS);
        assertTrue(diagnostic.report("16","151").contains("错误：无"));
    }
    @Test public void rawExceptionsCannotLeakSecretsThroughMessagesOrDiagnostics() {
        String secret="token=secret-account-at-private-url";
        Exception[] errors={new JSONException(secret),new SocketTimeoutException(secret),new IllegalStateException(secret)};
        String[] expected={"JSON_FORMAT","NETWORK_TIMEOUT","LOCAL_STATE_FAILURE"};
        for(int i=0;i<errors.length;i++) {
            PageInitFailure failure=PageInitFailure.from(errors[i]);
            assertEquals(expected[i],failure.code());assertFalse(failure.message().contains(secret));
            LoginDiagnostics diagnostic=new LoginDiagnostics();diagnostic.preparationFailed(PageInitStep.ROLES,failure.code());
            assertFalse(diagnostic.report("16","151").contains(secret));
        }
    }
}
