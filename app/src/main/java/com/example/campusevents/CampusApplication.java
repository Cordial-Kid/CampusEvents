package com.example.campusevents;
import android.app.Application;
import com.example.campusevents.bootstrap.AppGraph;
public final class CampusApplication extends Application {
    private AppGraph graph;
    @Override public void onCreate() { super.onCreate(); graph = new AppGraph(this); }
    public AppGraph graph() { return graph; }
}
