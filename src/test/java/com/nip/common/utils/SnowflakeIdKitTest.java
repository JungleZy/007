package com.nip.common.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdKitTest {

  // START_STAMP + 1_000 ms；测试内不得访问生产私有常量。
  private static final long BASE = 1_480_166_466_631L;

  // 按脚本依次返回墙钟毫秒；耗尽后保持返回最后一个值。
  private static final class ScriptedClock implements LongSupplier {
    private final long[] stamps;
    private int index = 0;

    ScriptedClock(long... stamps) {
      this.stamps = stamps;
    }

    @Override
    public long getAsLong() {
      long value = stamps[index];
      if (index < stamps.length - 1) {
        index++;
      }
      return value;
    }
  }

  // 测试本地解码器：复刻生产位布局（41 位时间戳 + START_STAMP），不依赖生产私有常量。
  private static long decodeMillis(long id) {
    return ((id >>> 22) & ((1L << 41) - 1)) + 1_480_166_465_631L;
  }

  @Test
  void rollbackUsesLogicalTimeWithoutDuplicateOrDescendingId() {
    ScriptedClock clock = new ScriptedClock(BASE, BASE - 1, BASE - 2, BASE + 1);
    SnowflakeIdKit ids = new SnowflakeIdKit(0, 0, clock);

    long first = ids.nextId();
    long second = ids.nextId();
    long third = ids.nextId();
    long recovered = ids.nextId();

    assertTrue(first < second && second < third && third < recovered);
    assertEquals(BASE, decodeMillis(first));
    assertEquals(BASE, decodeMillis(second));
    assertEquals(BASE, decodeMillis(third));
    assertEquals(BASE + 1, decodeMillis(recovered));
  }

  @Test
  void frozenClockPastSequenceCapacityAdvancesLogicalMillisecond() {
    SnowflakeIdKit ids = new SnowflakeIdKit(0, 0, () -> BASE);
    long previous = ids.nextId();
    Set<Long> generated = new HashSet<>();
    generated.add(previous);
    for (int i = 0; i < 5_000; i++) {
      long next = ids.nextId();
      assertTrue(next > previous);
      assertTrue(generated.add(next));
      previous = next;
    }
  }

  @Test
  void concurrentGenerationProducesUniqueIds() throws InterruptedException {
    SnowflakeIdKit ids = new SnowflakeIdKit(0, 0, () -> BASE);
    int threads = 8;
    int perThread = 2_000;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    Set<Long> generated = ConcurrentHashMap.newKeySet();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    for (int t = 0; t < threads; t++) {
      pool.submit(() -> {
        try {
          start.await();
          for (int i = 0; i < perThread; i++) {
            generated.add(ids.nextId());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertTrue(done.await(30, TimeUnit.SECONDS), "并发生成应在超时前完成");
    pool.shutdownNow();
    assertEquals(threads * perThread, generated.size(), "并发生成的 ID 必须全部唯一");
  }
}
