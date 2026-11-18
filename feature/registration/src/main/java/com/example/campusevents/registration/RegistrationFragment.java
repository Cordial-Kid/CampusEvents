package com.example.campusevents.registration;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.*;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.platform.invalidation.*;
import com.example.campusevents.platform.ui.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class RegistrationFragment extends Fragment {
    private final RegistrationApi api;
    private final AttendanceApi attendanceApi;
    private final DataInvalidation invalidation;
    private final Navigator navigator;
    private RegistrationViewModel model;
    private DataInvalidation.Subscription subscription;
    private long eventId;
    private boolean binding;
    public RegistrationFragment(RegistrationApi api,AttendanceApi attendanceApi,DataInvalidation invalidation,Navigator navigator) {
        super(R.layout.reg_content);this.api=api;this.attendanceApi=attendanceApi;this.invalidation=invalidation;this.navigator=navigator;
    }
    @Override public void onViewCreated(View view,Bundle state) {
        eventId=RouteArgs.fromBundle(getArguments()).eventId;
        model=new ViewModelProvider(this,new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) {return type.cast(new RegistrationViewModel(api,attendanceApi));}
        }).get(RegistrationViewModel.class);
        if(state!=null && !model.draftLoaded) {
            model.draftOwner=state.getLong("reg.owner",0);model.draft=state.getString("reg.draft","");model.rating=state.getInt("reg.rating",5);model.draftLoaded=state.getBoolean("reg.draftLoaded");
        }
        ((TextView)view.findViewById(R.id.reg_title)).setText(eventId==0?"我的报名":"报名与签到");
        view.findViewById(R.id.reg_detail).setVisibility(eventId==0?View.GONE:View.VISIBLE);
        view.findViewById(R.id.reg_refresh).setOnClickListener(v -> model.load(eventId));
        view.findViewById(R.id.reg_register).setOnClickListener(v -> model.act(eventId,0));
        view.findViewById(R.id.reg_cancel).setOnClickListener(v -> model.act(eventId,1));
        view.findViewById(R.id.reg_checkin).setOnClickListener(v -> model.act(eventId,2));
        view.findViewById(R.id.reg_submit).setOnClickListener(v -> model.act(eventId,3));
        EditText feedback=view.findViewById(R.id.reg_feedback);
        feedback.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {if(!binding) {model.draft=s.toString();model.draftLoaded=true;}}
            public void afterTextChanged(Editable e) {}
        });
        RatingBar rating=view.findViewById(R.id.reg_rating);
        rating.setOnRatingBarChangeListener((bar,value,fromUser) -> {if(fromUser) {model.rating=(int)value;model.draftLoaded=true;}});
        model.changes.observe(getViewLifecycleOwner(),ignored -> render(view));
        subscription=invalidation.subscribe(change -> {
            if(change.topic==ChangeTopic.SESSION) model.clearIdentity();
            if(change.topic==ChangeTopic.SESSION || change.topic==ChangeTopic.REGISTRATION || change.topic==ChangeTopic.EVENT_CONTENT) model.load(eventId);
        });
    }
    @Override public void onResume() {super.onResume();if(model!=null) model.resume(eventId);}
    @Override public void onDestroyView() {
        if(subscription!=null) subscription.unsubscribe();
        if(model!=null) model.cancelReads();super.onDestroyView();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if(model!=null) {state.putLong("reg.owner",model.draftOwner);state.putString("reg.draft",model.draft);state.putInt("reg.rating",model.rating);state.putBoolean("reg.draftLoaded",model.draftLoaded);}
    }
    private void render(View view) {
        TextView status=view.findViewById(R.id.reg_status);
        status.setText(model.message);
        if(eventId==0) {renderMine(view,status);return;}
        RegistrationSummary summary=model.summary!=null && model.summary.success?model.summary.data:null;
        ((TextView)view.findViewById(R.id.reg_summary)).setText(summary==null?
            model.summary==null?"正在加载报名信息…":model.summary.message:
            "已报名 "+summary.activeCount+" 人 · 剩余 "+summary.remainingCapacity+" 个名额\n我的状态："+status(summary.myStatus,summary.cancelReason)+"\n"+reason(summary.rejectionCode));
        view.findViewById(R.id.reg_register).setEnabled(!model.busy && summary!=null && summary.canRegister);
        view.findViewById(R.id.reg_cancel).setEnabled(!model.busy && summary!=null && summary.canCancel);
        AttendanceView attendance=model.attendance!=null && model.attendance.success?model.attendance.data:null;
        ((TextView)view.findViewById(R.id.reg_attendance_status)).setText(attendance==null?
            model.attendance==null?"正在加载签到信息…":model.attendance.message:
            attendance.message+(attendance.checkedInAt==null?"":"\n签到时间："+time(attendance.checkedInAt)));
        view.findViewById(R.id.reg_checkin).setEnabled(!model.busy && attendance!=null && attendance.canCheckIn);
        boolean editable=!model.busy && attendance!=null && attendance.canFeedback;
        view.findViewById(R.id.reg_submit).setEnabled(editable);
        EditText feedback=view.findViewById(R.id.reg_feedback);RatingBar rating=view.findViewById(R.id.reg_rating);
        feedback.setEnabled(editable);rating.setEnabled(editable);
        binding=true;
        if(!feedback.getText().toString().equals(model.draft)) {feedback.setText(model.draft);feedback.setSelection(feedback.length());}
        rating.setRating(model.rating);binding=false;
    }
    private void renderMine(View view,TextView status) {
        LinearLayout rows=view.findViewById(R.id.reg_rows);rows.removeAllViews();
        if(model.mine==null) {status.setText("正在加载…");return;}
        if(!model.mine.success) {status.setText(model.mine.message);return;}
        if(model.mine.data.isEmpty()) {status.setText("还没有报名记录，去活动列表看看吧");return;}
        for(RegistrationItem item:model.mine.data) {
            LinearLayout row=new LinearLayout(requireContext());row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,16,0,16);
            TextView text=new TextView(requireContext());text.setTextSize(16);
            text.setText(item.event.title+"\n"+time(item.event.startAt)+" · "+item.event.location+"\n"+status(item.status,item.cancelReason)+" · 报名于 "+time(item.registeredAt));row.addView(text);
            Button open=new Button(requireContext());open.setText("查看活动 / 签到反馈");open.setOnClickListener(v -> navigator.open(Route.event(ScreenKey.EVENT_DETAIL,item.event.id)));row.addView(open);rows.addView(row);
        }
    }
    private String time(long millis) {SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA);format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return format.format(new Date(millis));}
    private String status(RegistrationStatus status,CancelReason reason) {
        return status==RegistrationStatus.NONE?"未报名":status==RegistrationStatus.REGISTERED?"已报名":reason==CancelReason.EVENT_CANCELLED?"活动取消，报名已失效":"已取消报名";
    }
    private String reason(ResultCode code) {
        switch(code) {
            case OK:return "报名开放中";
            case NOT_LOGGED_IN:return "请选择学生身份后报名";
            case FORBIDDEN:return "管理员可查看人数，报名操作仅限学生";
            case EVENT_CANCELLED:return "活动已取消";
            case EVENT_STARTED:return "活动已开始，报名已关闭";
            case REGISTRATION_CLOSED:return "报名已截止；开始前仍可取消已有报名";
            case EVENT_FULL:return "名额已满";
            case ALREADY_REGISTERED:return "您已报名，请勿重复提交";
            default:return code.name();
        }
    }
}
