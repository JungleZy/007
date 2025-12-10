package com.nip.common.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5加密类（封装jdk自带的md5加密方法）
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2018-09-20 20:17:40
 */
public class MD5Util {

  public static String encrypt(String source) {
    return encodeMd5(source.getBytes());
  }

  private static String encodeMd5(byte[] source) {
    try {
      return encodeHex(MessageDigest.getInstance("MD5").digest(source));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private static String encodeHex(byte[] bytes) {
    StringBuilder buffer = new StringBuilder(bytes.length * 2);
    for (byte aByte : bytes) {
      if (((int) aByte & 0xff) < 0x10) {
        buffer.append("0");
      }
      buffer.append(Long.toString((int) aByte & 0xff, 16));
    }
    return buffer.toString();
  }

  public static void main(String[] args) {
    System.out.println(encrypt("123456a"));
  }
}
