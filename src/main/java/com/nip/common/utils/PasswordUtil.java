package com.nip.common.utils;

/**
 * 密码加密工具
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2018-09-25 11:43:38
 */
public class PasswordUtil {
  /**
   * 用户密码加密解密KEY
   */
  static final String PASSWORD_AES_KEY = "swrmArw81Y2uz3xLBPFEdg==";

  private static final String LINK = "\t";

  /**
   * 加密用户密码
   *
   * @param account
   * @param password
   *
   * @return
   *
   * @throws Exception
   */
  public static String encryptPassword(String account, String password) {
    StringBuilder sb = new StringBuilder();
    sb.append(account).append(LINK).append(password);
    try {
      return AESUtil.encrypt(sb.toString(), PASSWORD_AES_KEY);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * 解密用户密码
   *
   * @param encryptedPassword
   *
   * @return
   *
   * @throws Exception
   */
  public static String decryptPassword(String encryptedPassword) {
    String linked;
    try {
      linked = AESUtil.decrypt(encryptedPassword,PASSWORD_AES_KEY);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    return linked.split("\\t")[1];
  }

  public static void main(String[] args) throws Exception {
    System.out.println(encryptPassword("18623090141", "123456"));
    System.out.println(decryptPassword("L4POGvfa2Z+BVB09qA2n6wBrSvudoU7kwVZhrKlEGWY="));
  }
}
