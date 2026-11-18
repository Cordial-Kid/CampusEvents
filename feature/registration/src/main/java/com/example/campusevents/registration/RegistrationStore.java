package com.example.campusevents.registration;
import android.content.ContentValues;
import android.database.Cursor;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.platform.data.*;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.port.EventReadPort;
import java.util.*;

/** Eligibility and writes use the same scope, clock and transaction. */
final class RegistrationStore {
    private void fail(ResultCode code,String message) { throw new BusinessException(code,message); }
    private long student(ReadScope s) {
        if(s.actor()==null) fail(ResultCode.NOT_LOGGED_IN,"请先选择学生身份");
        if(s.actor().role!=Role.STUDENT) fail(ResultCode.FORBIDDEN,"此操作仅限学生");
        return s.actor().userId;
    }
    int count(ReadScope s,long id) {
        try(Cursor c=s.db().rawQuery("SELECT COUNT(*) FROM registrations WHERE eventId=? AND status='REGISTERED'",new String[]{""+id})) {
            c.moveToFirst(); return c.getInt(0);
        }
    }
    private RegistrationItem mine(ReadScope s,EventRecord e) {
        if(s.actor()==null || s.actor().role!=Role.STUDENT) return null;
        try(Cursor c=s.db().rawQuery("SELECT id,status,cancelReason,registeredAt,cancelledAt FROM registrations WHERE userId=? AND eventId=?",new String[]{""+s.actor().userId,""+e.id})) {
            if(!c.moveToFirst()) return null;
            return new RegistrationItem(c.getLong(0),e,RegistrationStatus.valueOf(c.getString(1)),CancelReason.valueOf(c.getString(2)),c.getLong(3),c.isNull(4)?null:c.getLong(4));
        }
    }
    RegistrationSummary summary(ReadScope s,EventRecord e) {
        RegistrationItem item=mine(s,e);
        RegistrationStatus status=item==null?RegistrationStatus.NONE:item.status;
        int n=count(s,e.id);
        ResultCode code=ResultCode.OK;
        if(s.actor()==null) code=ResultCode.NOT_LOGGED_IN;
        else if(s.actor().role!=Role.STUDENT) code=ResultCode.FORBIDDEN;
        else if(e.status==EventStatus.CANCELLED) code=ResultCode.EVENT_CANCELLED;
        else if(s.now()>=e.startAt) code=ResultCode.EVENT_STARTED;
        else if(s.now()>=e.registrationDeadline) code=ResultCode.REGISTRATION_CLOSED;
        else if(status==RegistrationStatus.REGISTERED) code=ResultCode.ALREADY_REGISTERED;
        else if(n>=e.capacity) code=ResultCode.EVENT_FULL;
        return new RegistrationSummary(n,Math.max(0,e.capacity-n),status,item==null?CancelReason.NONE:item.cancelReason,
            code==ResultCode.OK,code,status==RegistrationStatus.REGISTERED && e.status==EventStatus.ACTIVE && s.now()<e.startAt);
    }
    RegistrationSummary register(TxScope s,EventRecord e) {
        long user=student(s);RegistrationSummary before=summary(s,e);
        if(!before.canRegister) fail(before.rejectionCode,"当前无法报名："+before.rejectionCode);
        ContentValues v=new ContentValues();v.put("status","REGISTERED");v.put("cancelReason","NONE");
        v.put("registeredAt",s.now());v.putNull("cancelledAt");RegistrationItem old=mine(s,e);
        if(old==null) {v.put("userId",user);v.put("eventId",e.id);s.db().insertOrThrow("registrations",null,v);}
        else s.db().update("registrations",v,"id=?",new String[]{""+old.id});
        s.invalidate(ChangeTopic.REGISTRATION,e.id);return summary(s,e);
    }
    RegistrationSummary cancel(TxScope s,EventRecord e) {
        student(s);RegistrationItem item=mine(s,e);
        if(item==null || item.status==RegistrationStatus.CANCELLED) return summary(s,e);
        if(e.status==EventStatus.CANCELLED) fail(ResultCode.EVENT_CANCELLED,"活动已取消");
        if(s.now()>=e.startAt) fail(ResultCode.EVENT_STARTED,"活动已开始，不能取消报名");
        s.db().execSQL("UPDATE registrations SET status='CANCELLED',cancelReason='USER_CANCELLED',cancelledAt=? WHERE id=?",new Object[]{s.now(),item.id});
        s.invalidate(ChangeTopic.REGISTRATION,e.id);return summary(s,e);
    }
    void cancelEvent(TxScope s,long id) {
        s.db().execSQL("UPDATE registrations SET status='CANCELLED',cancelReason='EVENT_CANCELLED',cancelledAt=? WHERE eventId=? AND status='REGISTERED'",new Object[]{s.now(),id});
        s.invalidate(ChangeTopic.REGISTRATION,id);
    }
    List<RegistrationItem> mine(ReadScope s,EventReadPort events) {
        long user=student(s);List<RegistrationItem> result=new ArrayList<>();
        try(Cursor c=s.db().rawQuery("SELECT eventId FROM registrations WHERE userId=? ORDER BY registeredAt DESC,id DESC",new String[]{""+user})) {
            while(c.moveToNext()) result.add(mine(s,events.require(s,c.getLong(0))));
        }
        return Collections.unmodifiableList(result);
    }
    AttendanceView attendance(ReadScope s,EventRecord e) {
        student(s);RegistrationItem item=mine(s,e);Long at=null;Integer rating=null;String text="";
        if(item!=null) try(Cursor c=s.db().rawQuery("SELECT checkedInAt,rating,feedback FROM reg_attendance WHERE registrationId=?",new String[]{""+item.id})) {
            if(c.moveToFirst()) {at=c.getLong(0);rating=c.isNull(1)?null:c.getInt(1);text=c.getString(2);}
        }
        boolean active=item!=null && item.status==RegistrationStatus.REGISTERED && e.status==EventStatus.ACTIVE;
        String message=!active?"仅有效报名可签到和反馈":at!=null?"已签到，可提交或修改反馈":s.now()<e.startAt?"活动开始后可签到":s.now()>=e.endAt?"活动已结束，签到已关闭":"签到开放中";
        return new AttendanceView(s.actor().userId,at,rating,text,active && at==null && s.now()>=e.startAt && s.now()<e.endAt,active && at!=null,message);
    }
    AttendanceView checkIn(TxScope s,EventRecord e) {
        AttendanceView before=attendance(s,e);
        if(before.checkedInAt!=null) return before;
        if(!before.canCheckIn) fail(ResultCode.INVALID_INPUT,before.message);
        ContentValues v=new ContentValues();v.put("registrationId",mine(s,e).id);v.put("checkedInAt",s.now());
        s.db().insertOrThrow("reg_attendance",null,v);s.invalidate(ChangeTopic.REGISTRATION,e.id);return attendance(s,e);
    }
    AttendanceView feedback(TxScope s,EventRecord e,int rating,String text) {
        AttendanceView before=attendance(s,e);
        if(!before.canFeedback) fail(ResultCode.INVALID_INPUT,"请先完成有效活动签到");
        if(rating<1 || rating>5 || text==null || text.trim().isEmpty() || text.trim().length()>500)
            fail(ResultCode.INVALID_INPUT,"请选择1–5分并填写1–500字反馈");
        ContentValues v=new ContentValues();v.put("rating",rating);v.put("feedback",text.trim());v.put("submittedAt",s.now());
        s.db().update("reg_attendance",v,"registrationId=?",new String[]{""+mine(s,e).id});
        s.invalidate(ChangeTopic.REGISTRATION,e.id);return attendance(s,e);
    }
}
