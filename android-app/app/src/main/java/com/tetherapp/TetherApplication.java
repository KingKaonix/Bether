package com.tetherapp;

import android.app.Application;
import com.tetherapp.net.RoleManager;
import com.tetherapp.net.PerformanceMonitor;
import com.tetherapp.net.OptimizationSettings;

public class TetherApplication extends Application {
    private static TetherApplication instance;

    private RoleManager roleManager;
    private PerformanceMonitor performanceMonitor;
    private OptimizationSettings optimizationSettings;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        roleManager = new RoleManager(this);
        performanceMonitor = new PerformanceMonitor(this);
        optimizationSettings = new OptimizationSettings(this);

        // Detect role once at startup
        roleManager.detectRole();
    }

    public static TetherApplication get() {
        return instance;
    }

    public RoleManager getRoleManager() {
        return roleManager;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public OptimizationSettings getOptimizationSettings() {
        return optimizationSettings;
    }
}
