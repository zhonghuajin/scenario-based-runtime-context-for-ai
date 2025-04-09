package com.example.instrumentor.monitor;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * JUnit Platform Listener：当所有测试执行完毕后自动刷盘。
 * 
 * 这个方案不依赖 JVM shutdown hook，因此无论 Surefire 用 halt() 还是 exit()，
 * 都能保证在测试结束后把日志写到磁盘。
 */
public class LogFlushTestListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.err.println("[LogFlushTestListener] All tests finished, flushing logs...");
        try {
            LogMonitorServer.flushNow("junit-listener");
        } catch (Exception e) {
            System.err.println("[LogFlushTestListener] Flush failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}