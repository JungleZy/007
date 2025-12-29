package com.nip.performance;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("API并发性能测试")
class APIPerformanceTest {

    private static final int THREAD_COUNT = 10;
    private static final int REQUESTS_PER_THREAD = 100;
    private static final int MAX_RESPONSE_TIME_MS = 1000;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 18001;
    }

    @Test
    @Order(1)
    @DisplayName("测试用户登录API并发性能")
    void testLoginAPIConcurrentPerformance() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long[] responseTimes = new long[THREAD_COUNT * REQUESTS_PER_THREAD];
        AtomicInteger index = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                    long requestStart = System.currentTimeMillis();
                    try {
                        given()
                            .contentType(ContentType.JSON)
                            .body("{\"userAccount\":\"admin\",\"password\":\"123456\"}")
                        .when()
                            .post("/api/user/login")
                        .then()
                            .statusCode(200);
                        
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
        int totalRequests = THREAD_COUNT * REQUESTS_PER_THREAD;
        double throughput = (double) totalRequests / (totalDuration / 1000.0);
        double avgResponseTime = calculateAverage(responseTimes);
        long maxResponseTime = calculateMax(responseTimes);

        System.out.println("用户登录API并发性能测试结果:");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("失败请求数: " + failureCount.get());
        System.out.println("总耗时: " + totalDuration + " ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 请求/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + " ms");
        System.out.println("最大响应时间: " + maxResponseTime + " ms");

        assertTrue(successCount.get() > totalRequests * 0.95, "成功率低于95%");
        assertTrue(avgResponseTime < MAX_RESPONSE_TIME_MS, "平均响应时间超过阈值");
    }

    @Test
    @Order(2)
    @DisplayName("测试获取用户信息API并发性能")
    void testGetUserInfoAPIConcurrentPerformance() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        long[] responseTimes = new long[THREAD_COUNT * REQUESTS_PER_THREAD];
        AtomicInteger index = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                    long requestStart = System.currentTimeMillis();
                    try {
                        given()
                            .header("token", "test-token")
                            .header("deviceId", "test-device")
                        .when()
                            .get("/api/user/info")
                        .then()
                            .statusCode(200);
                        
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
        int totalRequests = THREAD_COUNT * REQUESTS_PER_THREAD;
        double throughput = (double) totalRequests / (totalDuration / 1000.0);
        double avgResponseTime = calculateAverage(responseTimes);
        long maxResponseTime = calculateMax(responseTimes);

        System.out.println("获取用户信息API并发性能测试结果:");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("失败请求数: " + failureCount.get());
        System.out.println("总耗时: " + totalDuration + " ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 请求/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + " ms");
        System.out.println("最大响应时间: " + maxResponseTime + " ms");

        assertTrue(successCount.get() > totalRequests * 0.95, "成功率低于95%");
        assertTrue(avgResponseTime < MAX_RESPONSE_TIME_MS, "平均响应时间超过阈值");
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
