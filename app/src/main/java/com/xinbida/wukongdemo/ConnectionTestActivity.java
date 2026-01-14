package com.xinbida.wukongdemo;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.interfaces.IConnectionStatus;
import com.xinbida.wukongim.message.type.WKConnectStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接功能测试 Activity
 * 用于测试各种网络场景下的连接状态和 UI 响应
 */
public class ConnectionTestActivity extends AppCompatActivity {

    private static final String TAG = "ConnectionTest";
    private TextView tvLog;
    private TextView tvStatus;
    private TextView tvFps;
    private ScrollView scrollView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    // FPS 监控
    private final AtomicLong lastFrameTime = new AtomicLong(System.nanoTime());
    private final AtomicInteger frameCount = new AtomicInteger(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);
    private boolean isFpsMonitorRunning = false;

    // UI 卡顿检测
    private final Handler anrHandler = new Handler(Looper.getMainLooper());
    private volatile long anrCheckStart = 0;
    private static final long ANR_THRESHOLD_MS = 300; // 超过300ms视为卡顿

    // 模拟网络状态
    private volatile boolean simulateNetworkOff = false;
    private volatile int simulateDelayMs = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_test);

        initViews();
        setupConnectionListener();
        startFpsMonitor();
        startAnrDetector();

        log("✅ 测试界面已启动");
//        log("当前UID: " + WKIM.getInstance().getOptions().getUid());
    }

    private void initViews() {
        tvLog = findViewById(R.id.tv_log);
        tvStatus = findViewById(R.id.tv_status);
        tvFps = findViewById(R.id.tv_fps);
        scrollView = findViewById(R.id.scroll_view);

        // 测试1: 正常连接
        findViewById(R.id.btn_connect_normal).setOnClickListener(v -> testNormalConnection());

        // 测试2: 模拟网络断开
        findViewById(R.id.btn_disconnect_network).setOnClickListener(v -> testNetworkDisconnect());

        // 测试3: 模拟网络恢复
        findViewById(R.id.btn_restore_network).setOnClickListener(v -> testNetworkRestore());

        // 测试4: 模拟网络延迟/不稳定
        findViewById(R.id.btn_slow_network).setOnClickListener(v -> testSlowNetwork());

        // 测试5: 压力测试 - 快速断连
        findViewById(R.id.btn_stress_test).setOnClickListener(v -> testStressConnection());

        // 测试6: 主动断开连接
        findViewById(R.id.btn_disconnect).setOnClickListener(v -> testManualDisconnect());

        // 测试7: UI 卡顿测试
        findViewById(R.id.btn_ui_stress).setOnClickListener(v -> testUIStress());

        // 清除日志
        findViewById(R.id.btn_clear_log).setOnClickListener(v -> {
            tvLog.setText("");
            log("日志已清除");
        });
    }

    private void setupConnectionListener() {
        WKIM.getInstance().getConnectionManager().addOnConnectionStatusListener(
                "ConnectionTestActivity",
                (status, reason) -> {
                    String statusText = getStatusText(status);
                    log("📡 连接状态变化: " + statusText + " (" + reason + ")");
                    updateStatusUI(status, statusText);
                    checkUIResponsiveness("连接状态回调");
                }
        );
    }

    // ============ 测试方法 ============

    /**
     * 测试1: 正常连接
     */
    private void testNormalConnection() {
        log("🔄 开始测试: 正常连接");
        simulateNetworkOff = false;
        simulateDelayMs = 0;

        long startTime = System.currentTimeMillis();
        WKIM.getInstance().getConnectionManager().connection();
        log("⏱️ connection() 调用耗时: " + (System.currentTimeMillis() - startTime) + "ms");
        checkUIResponsiveness("正常连接测试");
    }

    /**
     * 测试2: 模拟网络断开
     */
    private void testNetworkDisconnect() {
        log("🔴 开始测试: 模拟网络断开");
        simulateNetworkOff = true;

        long startTime = System.currentTimeMillis();
        WKIM.getInstance().getConnectionManager().disconnect(false);
        log("⏱️ disconnect() 调用耗时: " + (System.currentTimeMillis() - startTime) + "ms");
        checkUIResponsiveness("网络断开测试");
    }

    /**
     * 测试3: 模拟网络恢复
     */
    private void testNetworkRestore() {
        log("🟢 开始测试: 模拟网络恢复");
        simulateNetworkOff = false;
        simulateDelayMs = 0;

        long startTime = System.currentTimeMillis();
        WKIM.getInstance().getConnectionManager().connection();
        log("⏱️ connection() 调用耗时: " + (System.currentTimeMillis() - startTime) + "ms");
        checkUIResponsiveness("网络恢复测试");
    }

    /**
     * 测试4: 模拟网络延迟/不稳定
     */
    private void testSlowNetwork() {
        log("🟡 开始测试: 模拟慢网络 (延迟 2000ms)");
        simulateDelayMs = 2000;

        long startTime = System.currentTimeMillis();
        WKIM.getInstance().getConnectionManager().disconnect(false);

        mainHandler.postDelayed(() -> {
            WKIM.getInstance().getConnectionManager().connection();
            log("⏱️ 慢网络重连完成，总耗时: " + (System.currentTimeMillis() - startTime) + "ms");
            checkUIResponsiveness("慢网络测试");
        }, 500);
    }

    /**
     * 测试5: 压力测试 - 快速断连
     */
    private void testStressConnection() {
        log("⚡ 开始测试: 压力测试 - 快速断连10次");

        final int[] count = {0};
        final int maxCount = 10;

        Runnable stressTask = new Runnable() {
            @Override
            public void run() {
                if (count[0] >= maxCount) {
                    log("✅ 压力测试完成");
                    log("📊 丢帧数: " + droppedFrames.get());
                    return;
                }

                long startTime = System.currentTimeMillis();
                if (count[0] % 2 == 0) {
                    WKIM.getInstance().getConnectionManager().disconnect(false);
                    log("  [" + count[0] + "] disconnect 耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                } else {
                    WKIM.getInstance().getConnectionManager().connection();
                    log("  [" + count[0] + "] connection 耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                }

                checkUIResponsiveness("压力测试 #" + count[0]);
                count[0]++;
                mainHandler.postDelayed(this, 300);
            }
        };

        mainHandler.post(stressTask);
    }

    /**
     * 测试6: 主动断开连接
     */
    private void testManualDisconnect() {
        log("🔌 开始测试: 主动断开连接");

        long startTime = System.currentTimeMillis();
        WKIM.getInstance().getConnectionManager().disconnect(false);
        log("⏱️ disconnect(false) 调用耗时: " + (System.currentTimeMillis() - startTime) + "ms");
        checkUIResponsiveness("主动断开测试");
    }

    /**
     * 测试7: UI 卡顿测试 - 在连接操作期间进行大量 UI 操作
     */
    private void testUIStress() {
        log("🎨 开始测试: UI 压力测试");
        droppedFrames.set(0);

        // 同时进行连接操作和 UI 操作
        new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                WKIM.getInstance().getConnectionManager().disconnect(false);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                WKIM.getInstance().getConnectionManager().connection();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }
        }).start();

        // 同时频繁更新 UI
        final int[] uiCount = {0};
        Runnable uiTask = new Runnable() {
            @Override
            public void run() {
                if (uiCount[0] >= 100) {
                    log("✅ UI 压力测试完成");
                    log("📊 丢帧数: " + droppedFrames.get());
                    return;
                }

                tvFps.invalidate();
                uiCount[0]++;
                mainHandler.postDelayed(this, 10);
            }
        };
        mainHandler.post(uiTask);
    }



    // ============ 辅助方法 ============

    private String getStatusText(int status) {
        switch (status) {
            case WKConnectStatus.fail:
                return "连接失败";
            case WKConnectStatus.success:
                return "连接成功";
            case WKConnectStatus.kicked:
                return "被踢下线";
            case WKConnectStatus.syncMsg:
                return "同步消息中";
            case WKConnectStatus.connecting:
                return "连接中...";
            case WKConnectStatus.noNetwork:
                return "无网络";
            case WKConnectStatus.syncCompleted:
                return "同步完成";
            default:
                return "未知状态(" + status + ")";
        }
    }

    private void updateStatusUI(int status, String statusText) {
        int color;
        switch (status) {
            case WKConnectStatus.success:
            case WKConnectStatus.syncCompleted:
                color = 0xFF4CAF50; // 绿色
                break;
            case WKConnectStatus.connecting:
            case WKConnectStatus.syncMsg:
                color = 0xFFFF9800; // 橙色
                break;
            case WKConnectStatus.fail:
            case WKConnectStatus.noNetwork:
            case WKConnectStatus.kicked:
                color = 0xFFF44336; // 红色
                break;
            default:
                color = 0xFF9E9E9E; // 灰色
        }
        tvStatus.setText(statusText);
        tvStatus.setTextColor(color);
    }

    private void log(String message) {
        String timestamp = sdf.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";
        Log.d(TAG, message);

        if (Looper.myLooper() == Looper.getMainLooper()) {
            tvLog.append(logLine);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        } else {
            mainHandler.post(() -> {
                tvLog.append(logLine);
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            });
        }
    }

    // ============ FPS 监控 ============

    private void startFpsMonitor() {
        isFpsMonitorRunning = true;

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!isFpsMonitorRunning) return;

                long lastTime = lastFrameTime.getAndSet(frameTimeNanos);
                long frameDuration = (frameTimeNanos - lastTime) / 1_000_000; // 转换为毫秒

                // 超过 16.67ms (60fps) 视为丢帧
                if (frameDuration > 17) {
                    int dropped = (int) (frameDuration / 16) - 1;
                    if (dropped > 0) {
                        droppedFrames.addAndGet(dropped);
                        if (dropped > 2) {
                            log("⚠️ 检测到丢帧: " + dropped + " 帧 (" + frameDuration + "ms)");
                        }
                    }
                }

                frameCount.incrementAndGet();
                Choreographer.getInstance().postFrameCallback(this);
            }
        });

        // 每秒更新 FPS 显示
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFpsMonitorRunning) return;
                int fps = frameCount.getAndSet(0);
                tvFps.setText("FPS: " + fps + " | 丢帧: " + droppedFrames.get());
                mainHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    // ============ ANR 检测 ============

    private void startAnrDetector() {
        // 每 100ms 检查一次主线程响应
        new Thread(() -> {
            while (isFpsMonitorRunning) {
                anrCheckStart = System.currentTimeMillis();

                mainHandler.post(() -> {
                    long delay = System.currentTimeMillis() - anrCheckStart;
                    if (delay > ANR_THRESHOLD_MS) {
                        log("🚨 UI 卡顿警告! 主线程阻塞 " + delay + "ms");
                    }
                });

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "ANR-Detector").start();
    }

    private void checkUIResponsiveness(String context) {
        long startCheck = System.currentTimeMillis();
        mainHandler.post(() -> {
            long responseTime = System.currentTimeMillis() - startCheck;
            if (responseTime > 50) {
                log("⚠️ [" + context + "] UI 响应延迟: " + responseTime + "ms");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isFpsMonitorRunning = false;
        WKIM.getInstance().getConnectionManager().removeOnConnectionStatusListener("ConnectionTestActivity");
        mainHandler.removeCallbacksAndMessages(null);
        anrHandler.removeCallbacksAndMessages(null);
    }
}