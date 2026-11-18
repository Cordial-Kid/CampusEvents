package com.example.campusevents.registration;
import androidx.lifecycle.*;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.registration.*;
import java.util.*;

public final class RegistrationViewModel extends ViewModel {
    final MutableLiveData<Integer> changes=new MutableLiveData<>(0);
    ApiResult<RegistrationSummary> summary;
    ApiResult<AttendanceView> attendance;
    ApiResult<List<RegistrationItem>> mine;
    String message="", draft="";
    long draftOwner;
    int rating=5;
    boolean busy, draftLoaded;
    private final RegistrationApi api;
    private final AttendanceApi attendanceApi;
    private final List<CallHandle> reads=new ArrayList<>();
    private CallHandle write;
    private long generation;
    RegistrationViewModel(RegistrationApi api,AttendanceApi attendanceApi) {this.api=api;this.attendanceApi=attendanceApi;}
    private void signal() {changes.setValue(changes.getValue()+1);}
    void cancelReads() {generation++;for(CallHandle handle:reads) handle.cancelDelivery();reads.clear();}
    void clearIdentity() {cancelReads();summary=null;attendance=null;mine=null;message="";draft="";rating=5;draftOwner=0;draftLoaded=false;signal();}
    void resume(long id) {summary=null;attendance=null;mine=null;signal();load(id);}
    void load(long id) {
        cancelReads();long request=generation;
        if(id==0) reads.add(api.listMine().enqueue(r -> {if(request==generation) {mine=r;signal();}}));
        else {
            reads.add(api.getSummary(id).enqueue(r -> {if(request==generation) {summary=r;signal();}}));
            reads.add(attendanceApi.get(id).enqueue(r -> {
                if(request!=generation) return;
                attendance=r;
                if(r.success && draftOwner!=r.data.userId) {draft="";rating=5;draftLoaded=false;draftOwner=r.data.userId;}
                if(!r.success) {draft="";rating=5;draftOwner=0;draftLoaded=false;}
                if(r.success && !draftLoaded) {draft=r.data.feedback;rating=r.data.rating==null?5:r.data.rating;draftLoaded=true;}
                signal();
            }));
        }
    }
    void act(long id,int action) {
        if(busy) return;
        busy=true;message="正在提交…";signal();
        AsyncCall<?> call=action==0?api.register(id):action==1?api.cancelRegistration(id):action==2?attendanceApi.checkIn(id):attendanceApi.submitFeedback(id,rating,draft);
        write=call.enqueue(r -> {
            busy=false;write=null;message=r.success?"操作成功":r.message;signal();load(id);
        });
    }
    @Override protected void onCleared() {cancelReads();if(write!=null) write.cancelDelivery();}
}
