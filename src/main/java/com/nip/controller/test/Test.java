package com.nip.controller.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Test
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-09-06 10:33
 */
public class Test {
  /**
   * 数据压缩与解压算法，采用GZIP，平均耗时1ms
   *
   * @author  < a href=" ">ZhangYang</ a>
   * @version v1.0.01
   * @date    2023-09-06 10:48:04
   */

  public static void main(String[] args) throws IOException {
    long epochMilli = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    byte[] bytes = compressData("d".getBytes());
    long epochMilli1 = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    System.out.println(epochMilli1-epochMilli);
    byte[] bytes1 = decompressData(bytes);
    System.out.println(new String(bytes1));
    long epochMilli2 = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    System.out.println(epochMilli2-epochMilli1);
    System.out.println(epochMilli2-epochMilli);
  }

  public static byte[] compressData(byte[] data) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream gzipOS = new GZIPOutputStream(baos);
    gzipOS.write(data);
    gzipOS.close();
    byte[] compressedData = baos.toByteArray();
    baos.close();
    return compressedData;
  }

  public static byte[] decompressData(byte[] compressedData) throws IOException {
    ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
    GZIPInputStream gzipIS = new GZIPInputStream(bais);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int len;
    while ((len = gzipIS.read(buffer)) != -1) {
      baos.write(buffer, 0, len);
    }
    gzipIS.close();
    baos.close();

    return baos.toByteArray();
  }
}
