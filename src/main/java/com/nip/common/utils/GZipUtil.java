package com.nip.common.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据压缩与解压
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-09-25 10:21:27
 */
public class GZipUtil {

  /**
   * 数据压缩
   *
   * @param msg 待压缩的字符串
   * @return 压缩后的字节数组
   * @throws Exception 如果压缩过程中发生错误
   */
  public static byte[] doStringZip(String msg) throws Exception {
    byte[] input = msg.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream in = new ByteArrayInputStream(input);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    GZIPOutputStream gos = new GZIPOutputStream(os);
    int count;
    byte[] data = new byte[input.length];
    while ((count = in.read(data, 0, input.length)) != -1) {
      gos.write(data, 0, count);
    }
    gos.finish();
    gos.close();
    in.close();
    return os.toByteArray();
  }

  /**
   * 数据解压
   *
   * 该方法用于解压缩经过GZIP压缩的数据，并将其转换为字符串表示形式
   * 它主要用于处理网络传输或存储时被压缩以节省空间的数据
   *
   * @param bite 压缩后的字节数组，用于解压缩操作
   * @return 解压缩后的字符串，使用UTF-8编码
   * @throws IOException 如果在解压缩过程中发生I/O错误，将抛出此异常
   */
  public static String unStringZip(byte[] bite) throws IOException {
    if (bite == null || bite.length == 0) {
      throw new IllegalArgumentException("Input byte array is null or empty");
    }

    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
         ByteArrayInputStream in = new ByteArrayInputStream(bite);
         GZIPInputStream gzip = new GZIPInputStream(in)) {

      byte[] buffer = new byte[8192];
      int n;
      while ((n = gzip.read(buffer)) >= 0) {
        out.write(buffer, 0, n);
      }
      return out.toString(StandardCharsets.UTF_8);
    }
  }

  /**
   * 文件压缩
   *
   * @return
   *
   * @throws Exception
   */
  public static byte[] doFileZip(byte[] data) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    GZIPOutputStream gzip = new GZIPOutputStream(bos);
    gzip.write(data);
    gzip.finish();
    gzip.close();
    byte[] ret = bos.toByteArray();
    bos.close();
    return ret;
  }

  /**
   * 文件解压
   *
   * @return
   *
   * @throws Exception
   */
  public static byte[] unFileZip(byte[] data) throws Exception {
    ByteArrayInputStream bis = new ByteArrayInputStream(data);
    GZIPInputStream gzip = new GZIPInputStream(bis);
    byte[] buf = new byte[1024];
    int num;
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    while ((num = gzip.read(buf, 0, buf.length)) != -1) {
      bos.write(buf, 0, num);
    }
    gzip.close();
    bis.close();
    byte[] ret = bos.toByteArray();
    bos.flush();
    bos.close();
    return ret;
  }
  public static void main(String[] args) throws Exception {

    // 读取文件
    String readPath = "C:\\Users\\zhang\\Desktop\\1.jpg";
    File file = new File(readPath);
    FileInputStream in = new FileInputStream(file);
    byte[] data = new byte[in.available()];
    in.read(data);
    in.close();

    System.out.println("文件原始大小:" + data.length);

    // 测试压缩
    byte[] ret1 = doFileZip(data);
    System.out.println("压缩之后大小:" + ret1.length);

    // 还原文件
    byte[] ret2 = unFileZip(ret1);
    System.out.println("还原之后大小:" + ret2.length);

    // 写出文件
    String writePath = "C:\\Users\\zhang\\Desktop\\2.jpg";
    FileOutputStream fos = new FileOutputStream(writePath);
    fos.write(ret2);
    fos.close();
  }
}
