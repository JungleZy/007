package com.nip.performance;

import com.nip.dao.UserDao;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("数据库操作性能测试")
class DatabasePerformanceTest {

    @Inject
    UserDao userDao;

    private static final int THREAD_COUNT = 5;
    private static final int OPERATIONS_PER_THREAD = 50;

    @Test
    @Order(1)
    @DisplayName("测试并发查询性能")
    void testConcurrentQueryPerformance() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long[] responseTimes = new long[THREAD_COUNT * OPERATIONS_PER_THREAD];
        AtomicInteger index = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                    long requestStart = System.currentTimeMillis();
                    try {
                        userDao.findAll().list();
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                    long requestEnd = System.currentTimeMillis();
                    responseTimes[index.getAndIncrement()] = requestEnd - requestStart;
                }
                latch.countDown();
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();
        long endTime = System.currentTimeMillis();

        long totalDuration = endTime - startTime;
        int totalOperations = THREAD_COUNT * OPERATIONS_PER_THREAD;
        double throughput = (double) totalOperations / (totalDuration / 1000.0);
        double avgResponseTime = calculateAverage(responseTimes);
        long maxResponseTime = calculateMax(responseTimes);

        System.out.println("并发查询性能测试结果:");
        System.out.println("总操作数: " + totalOperations);
        System.out.println("成功操作数: " + successCount.get());
        System.out.println("失败操作数: " + failureCount.get());
        System.out.println("总耗时: " + totalDuration + " ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 操作/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + " ms");
        System.out.println("最大响应时间: " + maxResponseTime + " ms");

        assertTrue(successCount.get() > totalOperations * 0.95, "成功率低于95%");
        assertTrue(avgResponseTime < 500, "平均响应时间超过阈值");
    }

    @Test
    @Order(2)
    @DisplayName("测试批量查询性能")
    void testBatchQueryPerformance() {
        int[] batchSizes = {10, 50, 100, 500};
        
        System.out.println("批量查询性能测试结果:");
        System.out.println("批次大小\t总耗时(ms)\t平均耗时(ms)");
        
        for (int batchSize : batchSizes) {
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 10; i++) {
                userDao.findAll().page(0, batchSize).list();
            }
            
            long endTime = System.currentTimeMillis();
            long totalDuration = endTime - startTime;
            double avgDuration = (double) totalDuration / 10;
            
            System.out.println(batchSize + "\t\t" + totalDuration + "\t\t" + String.format("%.2f", avgDuration));
            
            assertTrue(avgDuration < 1000, "批次大小 " + batchSize + " 的查询性能不达标");
        }
    }

    private double calculateAverage(long[] array) {
        long sum = 0;
        int count = 0;
        for (long value : array) {
            if (value > 0) {
                sum += value;
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 0;
    }

    private long calculateMax(long[] array) {
        long max = 0;
        for (long value : array) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }
}
