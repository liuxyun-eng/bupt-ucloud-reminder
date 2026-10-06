package cn.edu.bupt.cloudpost;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import org.json.*;

public final class RulesTest {
    @Test public void historicalBaselineAndReorderedOldRowsDoNotNotify(){
        Models.Notice n=new Models.Notice("old","title","body",1000,false);
        assertFalse(Core.isNew(n,false,new HashSet<>(),0));
        assertFalse(Core.isNew(n,true,new HashSet<>(),2000));
        assertFalse(Core.isNew(n,true,new HashSet<>(Arrays.asList("old")),1000));
        assertTrue(Core.isNew(new Models.Notice("new","title","body",2000,false),true,new HashSet<>(),2000));
    }
    @Test public void urgentAndDelayedRemindersChooseOnlyCurrentStage(){
        long now=1_800_000_000_000L;
        assertEquals(-1,Core.currentStage(now+25*Core.HOUR,now));
        assertEquals(24,Core.currentStage(now+24*Core.HOUR,now));
        assertEquals(2,Core.currentStage(now+2*Core.HOUR,now));
        assertEquals(2,Core.currentStage(now+5*60_000,now));
        assertEquals(0,Core.currentStage(now-1,now));
        assertEquals(-1,Core.currentStage(0,now));
    }
    @Test public void changedDeadlineGetsNewReminderIdentity(){
        Models.Task before=new Models.Task("a","作业","","作业",1000,"url");
        Models.Task after=new Models.Task("a","作业","","作业",2000,"url");
        assertNotEquals(Core.reminderKey(before,24),Core.reminderKey(after,24));
        assertNotEquals(Core.reminderKey(before,24),Core.reminderKey(before,2));
    }
    @Test public void platformLocalTimesUseBeijingRegardlessOfPhoneZone(){
        assertEquals(1_791_475_140_000L,Models.time("2026-10-08 23:59"));
        assertEquals(Models.time("2026-10-08 23:59"),Models.time("2026-10-08T15:59:00Z"));
        assertEquals(Models.time("2026-10-08 23:59"),Models.time("2026-10-08 23:59:00"));
        assertEquals(0,Models.time(null));
    }
    @Test public void taskResponseIncludesAllRowsBeyondHomepageSixAndEscapesTitles() throws Exception {
        JSONArray a=new JSONArray();
        for(int i=0;i<12;i++)a.put(new JSONObject().put("type",3).put("activityId","activity-"+i)
            .put("activityName","作业 & x=1").put("endTime","2026-10-08 23:59").put("studentGroupId","g"));
        List<Models.Task> tasks=ApiParser.tasks(new JSONObject().put("undoneNum",12).put("undoneList",a));
        assertEquals(12,tasks.size());
        assertTrue(tasks.get(0).url().contains("%26"));
        assertTrue(tasks.get(0).url().contains("assignmentId=activity-0"));
    }
    @Test(expected=JSONException.class) public void incompleteTasksAreRejectedInsteadOfClearingCache() throws Exception {
        ApiParser.tasks(new JSONObject().put("undoneNum",4).put("undoneList",new JSONArray()));
    }
    @Test(expected=JSONException.class) public void loginOrErrorResponseIsRejected() throws Exception {
        ApiParser.data(new JSONObject().put("code",401).put("data",new JSONObject()));
    }
    @Test(expected=IllegalArgumentException.class) public void unsupportedTimeDoesNotBecomeAlreadyOverdue(){Models.time("明天");}
    @Test public void notificationReadStateAndLargeIdentifiersArePreserved() throws Exception {
        JSONObject n=new JSONObject().put("id","1318863781576577025").put("newsTitle","测试课程").put("newsInfo","<span>作业</span>").put("newsCopyTime","2026-10-01 10:00").put("isRead",0);
        List<Models.Notice> parsed=ApiParser.notices(new JSONObject().put("records",new JSONArray().put(n)));
        assertEquals("1318863781576577025",parsed.get(0).id());assertTrue(parsed.get(0).unread());
    }
}
