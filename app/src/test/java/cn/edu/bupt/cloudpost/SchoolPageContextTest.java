package cn.edu.bupt.cloudpost;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public final class SchoolPageContextTest {
    private static final String TARGET="https://ucloud.bupt.edu.cn/uclass/course.html#/student/assignmentDetails_fullpage?assignmentId=example";
    private static JSONObject response(JSONArray data) throws Exception {return new JSONObject().put("code",200).put("success",true).put("data",data);}
    private static JSONObject role(String alias,String dept,String id) throws Exception {
        return new JSONObject().put("id","membership-"+id).put("roleId",id).put("roleAliase",alias).put("dept",dept).put("roleName","学生");
    }
    @Test public void correctStudentRoleIsSelectedAmongOtherIdentities() throws Exception {
        JSONArray roles=new JSONArray().put(role("JS004","same","teacher-role")).put(role("JS005","other","other-role")).put(role("JS005","same","student-role"));
        SchoolPageContext.Role chosen=SchoolPageContext.studentRole(response(roles),"JS005:same","");
        assertEquals("student-role",chosen.selected().getString("roleId"));assertEquals(3,chosen.all().length());
    }
    @Test public void persistedRoleDistinguishesMultipleStudentRoles() throws Exception {
        JSONArray roles=new JSONArray().put(role("JS005","same","r1")).put(role("JS005","same","r2"));
        assertEquals("r2",SchoolPageContext.studentRole(response(roles),"JS005:same","r2").selected().getString("roleId"));
    }
    @Test public void nullDepartmentMatchesOfficialJavascriptIdentity() throws Exception {
        JSONObject student=role("JS005","unused","r1").put("dept",JSONObject.NULL);
        SchoolPageContext.Role chosen=SchoolPageContext.studentRole(response(new JSONArray().put(student)),"JS005:null","r1");
        assertTrue(chosen.selected().has("dept"));assertTrue(chosen.selected().isNull("dept"));
        assertEquals("r1",chosen.selected().getString("roleId"));
    }
    @Test public void absentDepartmentMatchesUndefinedAndIsNotChangedToEmpty() throws Exception {
        JSONObject student=role("JS005","unused","r1");student.remove("dept");
        SchoolPageContext.Role chosen=SchoolPageContext.studentRole(response(new JSONArray().put(student)),"JS005:undefined","");
        assertFalse(chosen.selected().has("dept"));
        try {SchoolPageContext.studentRole(response(new JSONArray().put(student)),"JS005:","");fail("Different identity accepted");}
        catch(SchoolPageContext.Problem p){assertEquals("ROLE_NO_MATCH",p.diagnostic);}
    }
    @Test public void staleRoleIdCanUseOnlyAnUnambiguousMatchingIdentity() throws Exception {
        JSONArray roles=new JSONArray().put(role("JS005","same","r1"));
        assertEquals("r1",SchoolPageContext.studentRole(response(roles),"JS005:same","old-role").selected().getString("roleId"));
        roles.put(role("JS005","same","r2"));
        try {SchoolPageContext.studentRole(response(roles),"JS005:same","old-role");fail("Ambiguous identity accepted");}
        catch(SchoolPageContext.Problem p){assertEquals("ROLE_AMBIGUOUS",p.diagnostic);}
    }
    @Test public void successfulPortalDataDoesNotRequireAnOptionalCodeField() throws Exception {
        JSONObject roles=new JSONObject().put("data",new JSONArray().put(role("JS005","same","r1")));
        assertEquals("r1",SchoolPageContext.studentRole(roles,"JS005:same","").selected().getString("roleId"));
        assertEquals(Arrays.asList("stuAssignmentInfo"),SchoolPageContext.grants(new JSONObject().put("data",new JSONArray().put(new JSONObject().put("code","stuAssignmentInfo")))));
        assertEquals("current-account",SchoolPageContext.userInfo(new JSONObject().put("data",new JSONObject().put("id","current-account")),"current-account").getString("id"));
    }
    @Test(expected=JSONException.class) public void ambiguousLegacyRoleRequiresReconnection() throws Exception {
        SchoolPageContext.studentRole(response(new JSONArray().put(role("JS005","same","r1")).put(role("JS005","same","r2"))),"JS005:same","");
    }
    @Test(expected=JSONException.class) public void aDifferentDepartmentCannotSupplyPermissions() throws Exception {
        SchoolPageContext.studentRole(response(new JSONArray().put(role("JS005","other","r1"))),"JS005:same","");
    }
    @Test public void permissionCodesComeFromSuccessfulServerResponseOnly() throws Exception {
        JSONArray menu=new JSONArray().put(new JSONObject().put("code","stuAssignmentInfo"))
            .put(new JSONObject().put("title","parent"))
            .put(new JSONObject().put("code",""))
            .put(new JSONObject().put("code","stuAssignmentInfo"))
            .put(new JSONObject().put("code","stuExamInfo"));
        assertEquals(Arrays.asList("stuAssignmentInfo","stuExamInfo"),SchoolPageContext.grants(response(menu)));
    }
    @Test(expected=JSONException.class) public void permissionErrorIsNeverConvertedIntoAnEmptySuccessfulGrant() throws Exception {
        SchoolPageContext.grants(new JSONObject().put("code",403).put("data",new JSONArray()));
    }
    @Test(expected=JSONException.class) public void failedSuccessFlagIsRejected() throws Exception {
        SchoolPageContext.grants(response(new JSONArray()).put("success",false));
    }
    @Test public void missingPermissionRemainsBlockedRatherThanFabricated() {
        try {SchoolPageContext.requirePageGrant(TARGET,Arrays.asList("stuAssignmentList"));fail("Missing grant bypassed");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("学校返回"));}
        SchoolPageContext.requirePageGrant(TARGET,Arrays.asList("stuAssignmentInfo"));
    }
    @Test public void officialLocalStorageEncodingMatchesCourseGuard() throws Exception {
        SchoolPageContext.Role r=SchoolPageContext.studentRole(response(new JSONArray().put(role("JS005","same","r1"))),"JS005:same","");
        String script=SchoolPageContext.storageScript(r,Arrays.asList("stuAssignmentInfo","stuExamInfo"),SchoolAuth.Client.PORTAL);
        String prefix="localStorage.setItem('user-role-permission',";
        int start=script.indexOf(prefix)+prefix.length(),end=script.indexOf(");",start);
        String stored=new JSONArray("["+script.substring(start,end)+"]").getString(0);
        String officialValue=new JSONArray("["+stored+"]").getString(0);
        assertTrue(Arrays.asList(officialValue.split(",")).contains("stuAssignmentInfo"));
        assertTrue(script.contains("window.origin!=="));assertTrue(script.contains("'fromPortal'"));
        assertFalse(script.contains("meta.code"));assertFalse(script.contains("view=student"));
    }
    @Test public void largeRoleAndUserIdentifiersArePreservedAsStrings() throws Exception {
        JSONObject student=role("JS005","same","r").put("roleId",1318863781576577025L);
        assertEquals("1318863781576577025",SchoolPageContext.studentRole(response(new JSONArray().put(student)),"JS005:same","").selected().getString("roleId"));
        JSONObject user=new JSONObject().put("id",1318863781576577025L).put("realName","测试").put("password","unused-secret");
        JSONObject safe=SchoolPageContext.userInfo(new JSONObject().put("code",200).put("data",user),"1318863781576577025");
        assertEquals("1318863781576577025",safe.getString("id"));assertFalse(safe.has("password"));
    }
    @Test(expected=JSONException.class) public void mismatchedUserInfoIsRejected() throws Exception {
        SchoolPageContext.userInfo(new JSONObject().put("code",200).put("data",new JSONObject().put("id","other-account")),"current-account");
    }
    @Test public void courseClientClearsOldPortalMarker() throws Exception {
        SchoolPageContext.Role r=SchoolPageContext.studentRole(response(new JSONArray().put(role("JS005","same","r1"))),"JS005:same","");
        assertTrue(SchoolPageContext.storageScript(r,Collections.emptyList(),SchoolAuth.Client.COURSE).contains("removeItem('ykt_login_from')"));
    }
    @Test public void malformedDataReportsItsSpecificFailureWithoutResponseContents() throws Exception {
        try {SchoolPageContext.studentRole(new JSONObject().put("code",200).put("data","private-content"),"JS005:same","");fail("Bad data accepted");}
        catch(SchoolPageContext.Problem p){assertEquals("RESPONSE_ARRAY_FORMAT",p.diagnostic);assertFalse(p.getMessage().contains("private-content"));}
        try {SchoolPageContext.userInfo(new JSONObject().put("data",new JSONObject()),"current-account");fail("Missing account accepted");}
        catch(SchoolPageContext.Problem p){assertEquals("USER_ID_MISSING",p.diagnostic);}
        try {SchoolPageContext.grants(new JSONObject().put("code",500).put("data",new JSONArray()));fail("Failure accepted");}
        catch(SchoolPageContext.Problem p){assertEquals("RESPONSE_CODE_500",p.diagnostic);}
    }
    @Test public void javascriptResultsAreRestrictedToKnownCodes() {
        assertEquals("STORAGE_OK",SchoolPageContext.storageResult("\"STORAGE_OK\""));
        assertEquals("STORAGE_WRITE",SchoolPageContext.storageResult("\"STORAGE_WRITE\""));
        for(String result:new String[]{null,"null","true","\"private-account-details\"","{}","[\"STORAGE_OK\"]"})
            assertEquals("STORAGE_RESULT_INVALID",SchoolPageContext.storageResult(result));
    }
}
