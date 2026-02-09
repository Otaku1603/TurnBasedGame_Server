package com.game.fwork.manager;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Lua 性能监控器
 * 统计 Lua 函数的调用次数和平均耗时，用于在管理后台监控脚本性能
 */
@Component
public class LuaPerformanceMonitor {

    /**
     * 调用次数统计
     */
    private final Map<String, AtomicLong> callCounts = new ConcurrentHashMap<>();

    /**
     * 总耗时统计（毫秒）
     */
    private final Map<String, AtomicLong> totalExecutionTime = new ConcurrentHashMap<>();

    /**
     * 最后一次调用时间
     */
    private final Map<String, Long> lastCallTime = new ConcurrentHashMap<>();

    /**
     * 记录一次Lua函数调用
     *
     * @param functionName 函数名
     * @param executionTimeMs 执行耗时（毫秒）
     */
    public void recordCall(String functionName, long executionTimeMs) {
        // 增加调用次数
        callCounts.computeIfAbsent(functionName, k -> new AtomicLong(0))
                .incrementAndGet();

        // 累加总耗时
        totalExecutionTime.computeIfAbsent(functionName, k -> new AtomicLong(0))
                .addAndGet(executionTimeMs);

        // 更新最后调用时间
        lastCallTime.put(functionName, System.currentTimeMillis());
    }

    /**
     * 获取函数调用次数
     *
     * @param functionName 函数名
     * @return 调用次数
     */
    public long getCallCount(String functionName) {
        AtomicLong count = callCounts.get(functionName);
        return count != null ? count.get() : 0;
    }

    /**
     * 获取函数总调用次数
     *
     * @return 所有函数的总调用次数
     */
    public long getTotalCallCount() {
        return callCounts.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    /**
     * 获取函数平均执行时间
     *
     * @param functionName 函数名
     * @return 平均耗时（毫秒），如果没有调用记录则返回0
     */
    public double getAverageExecutionTime(String functionName) {
        long count = getCallCount(functionName);
        if (count == 0) {
            return 0.0;
        }

        AtomicLong totalTime = totalExecutionTime.get(functionName);
        if (totalTime == null) {
            return 0.0;
        }

        return (double) totalTime.get() / count;
    }

    /**
     * 获取所有函数的统计信息
     *
     * @return Map，Key是函数名，Value是统计信息
     */
    public Map<String, FunctionStats> getAllStats() {
        Map<String, FunctionStats> result = new ConcurrentHashMap<>();

        for (String functionName : callCounts.keySet()) {
            long count = getCallCount(functionName);
            double avgTime = getAverageExecutionTime(functionName);
            Long lastCall = lastCallTime.get(functionName);

            FunctionStats stats = new FunctionStats(
                    functionName,
                    count,
                    avgTime,
                    lastCall
            );

            result.put(functionName, stats);
        }

        return result;
    }

    /**
     * 重置所有统计数据
     */
    public void resetStats() {
        callCounts.clear();
        totalExecutionTime.clear();
        lastCallTime.clear();
    }

    /**
     * 函数统计信息（内部类）
     */
    public static class FunctionStats {
        private final String functionName;
        private final long callCount;
        private final double averageExecutionTime;
        private final Long lastCallTime;

        public FunctionStats(String functionName, long callCount,
                             double averageExecutionTime, Long lastCallTime) {
            this.functionName = functionName;
            this.callCount = callCount;
            this.averageExecutionTime = averageExecutionTime;
            this.lastCallTime = lastCallTime;
        }

        public String getFunctionName() {
            return functionName;
        }

        public long getCallCount() {
            return callCount;
        }

        public double getAverageExecutionTime() {
            return averageExecutionTime;
        }

        public Long getLastCallTime() {
            return lastCallTime;
        }
    }
}