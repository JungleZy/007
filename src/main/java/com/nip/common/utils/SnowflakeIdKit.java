package com.nip.common.utils;

/**
 * SnowflakeIdKit
 *
 * * ----------------------------
 * *  * Desc:id生成工具，使用Twitter的Snowflake算法
 * *  * SnowFlake的结构如下(每部分用-分开):<br>
 * *  * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000 <br>
 * *  * 1位标识，由于long基本类型在Java中是带符号的，最高位是符号位，正数是0，负数是1，所以id一般是正数，最高位是0<br>
 * *  * 41位时间截(毫秒级)，注意，41位时间截不是存储当前时间的时间截，而是存储时间截的差值（当前时间截 - 开始时间截)
 * *  * 得到的值），这里的的开始时间截，一般是我们的id生成器开始使用的时间，由我们程序来指定的（如下下面程序IdWorker类的startTime属性）。41位的时间截，可以使用69年，年T = (1L << 41) / (1000L * 60 * 60 * 24 * 365) = 69<br>
 * *  * 10位的数据机器位，可以部署在1024个节点，包括5位datacenterId和5位workerId<br>
 * *  * 12位序列，毫秒内的计数，12位的计数顺序号支持每个节点每毫秒(同一机器，同一时间截)产生4096个ID序号<br>
 * *  * 加起来刚好64位，为一个Long型。<br>
 * *  * SnowFlake的优点是，整体上按照时间自增排序，并且整个分布式系统内不会产生ID碰撞(由数据中心ID和机器ID作区分)，并且效率较高，经测试，SnowFlake每秒能够产生26万ID左右。
 *
 * @author Nandem(nandem @ 126.com)
 * @version v1.0.01
 * @date 2018-10-09 22:25:10
 */
public class SnowflakeIdKit {
  /*
   * 起始的时间戳
   */
  private final static long START_STAMP = 1480166465631L;

  /*
   * 每一部分占用的位数
   */
  private final static long SEQUENCE_BIT = 12; //序列号占用的位数
  private final static long MACHINE_BIT = 5;  //机器标识占用的位数
  private final static long DATA_CENTER_BIT = 5;//数据中心占用的位数

  /*
   * 每一部分的最大值
   */
  private final static long MAX_DATA_CENTER_NUM = ~(-1L << DATA_CENTER_BIT);
  private final static long MAX_MACHINE_NUM = ~(-1L << MACHINE_BIT);
  private final static long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

  /*
   * 每一部分向左的位移
   */
  private final static long MACHINE_LEFT = SEQUENCE_BIT;
  private final static long DATA_CENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
  private final static long TIME_STAMP_LEFT = DATA_CENTER_LEFT + DATA_CENTER_BIT;

  private long dataCenterId;  //数据中心
  private long machineId;    //机器标识
  private long sequence = 0L; //序列号
  private long lastStamp = -1L;//上一次时间戳

  private SnowflakeIdKit(long dataCenterId, long machineId) {
    if (dataCenterId > MAX_DATA_CENTER_NUM || dataCenterId < 0) {
      throw new IllegalArgumentException("dataCenterId can't be greater than MAX_DATA_CENTER_NUM or less than 0");
    }

    if (machineId > MAX_MACHINE_NUM || machineId < 0) {
      throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
    }

    this.dataCenterId = dataCenterId;
    this.machineId = machineId;
  }

  /**
   * 产生下一个ID
   *
   * @return id
   */
  public synchronized long nextId() {
    long currStamp = getNewStamp();
    if (currStamp < lastStamp) {
      throw new IllegalArgumentException("Clock moved backwards.  Refusing to generate id");
    }

    if (currStamp == lastStamp) {
      //相同毫秒内，序列号自增
      sequence = (sequence + 1) & MAX_SEQUENCE;
      //同一毫秒的序列数已经达到最大
      if (sequence == 0L) {
        currStamp = getNextMill();
      }
    } else {
      //不同毫秒内，序列号置为0
      sequence = 0L;
    }

    lastStamp = currStamp;

    return (currStamp - START_STAMP) << TIME_STAMP_LEFT //时间戳部分
      | dataCenterId << DATA_CENTER_LEFT      //数据中心部分
      | machineId << MACHINE_LEFT            //机器标识部分
      | sequence;                            //序列号部分
  }

  private long getNextMill() {
    long mill = getNewStamp();
    while (mill <= lastStamp) {
      mill = getNewStamp();
    }
    return mill;
  }

  private long getNewStamp() {
    return System.currentTimeMillis();
  }

  private static volatile SnowflakeIdKit instance = null;

  public static SnowflakeIdKit getInstance() {
    if (instance == null) {
      synchronized (SnowflakeIdKit.class) {
        if (instance == null) {
          instance = new SnowflakeIdKit(0, 0);
        }
      }
    }
    return instance;
  }
}
