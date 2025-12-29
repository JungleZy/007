package com.nip.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordUtil工具类单元测试")
class PasswordUtilTest {

    private static final String PHONE = "18623090141";
    private static final String PASSWORD = "123456";

    @Test
    @DisplayName("测试密码加密")
    void testEncryptPassword() {
        String encrypted = PasswordUtil.encryptPassword(PHONE, PASSWORD);
        
        assertNotNull(encrypted);
        assertNotEquals(PASSWORD, encrypted);
        assertTrue(encrypted.length() > 0);
    }

    @Test
    @DisplayName("测试密码解密")
    void testDecryptPassword() {
        String encrypted = PasswordUtil.encryptPassword(PHONE, PASSWORD);
        String decrypted = PasswordUtil.decryptPassword(encrypted);
        
        assertNotNull(decrypted);
        assertEquals(PASSWORD, decrypted);
    }

    @Test
    @DisplayName("测试加密解密一致性")
    void testEncryptDecryptConsistency() {
        String original = "testPassword123";
        String encrypted = PasswordUtil.encryptPassword(PHONE, original);
        String decrypted = PasswordUtil.decryptPassword(encrypted);
        
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("测试不同手机号产生不同加密结果")
    void testDifferentPhoneDifferentResult() {
        String phone1 = "18623090141";
        String phone2 = "18623090142";
        
        String result1 = PasswordUtil.encryptPassword(phone1, PASSWORD);
        String result2 = PasswordUtil.encryptPassword(phone2, PASSWORD);
        
        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("测试相同手机号和密码产生相同加密结果")
    void testSamePhoneSameResult() {
        String result1 = PasswordUtil.encryptPassword(PHONE, PASSWORD);
        String result2 = PasswordUtil.encryptPassword(PHONE, PASSWORD);
        
        assertEquals(result1, result2);
    }
}
