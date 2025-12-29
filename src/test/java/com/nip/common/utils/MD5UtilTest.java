package com.nip.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MD5Util工具类单元测试")
class MD5UtilTest {

    @Test
    @DisplayName("测试MD5加密 - 普通字符串")
    void testEncryptNormalString() {
        String input = "password123";
        String encrypted = MD5Util.encrypt(input);
        
        assertNotNull(encrypted);
        assertEquals(32, encrypted.length());
        assertNotEquals(input, encrypted);
    }

    @Test
    @DisplayName("测试MD5加密 - 相同输入产生相同输出")
    void testEncryptConsistency() {
        String input = "test123";
        String result1 = MD5Util.encrypt(input);
        String result2 = MD5Util.encrypt(input);
        
        assertEquals(result1, result2);
    }

    @Test
    @DisplayName("测试MD5加密 - 不同输入产生不同输出")
    void testEncryptDifferentInputs() {
        String result1 = MD5Util.encrypt("password1");
        String result2 = MD5Util.encrypt("password2");
        
        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("测试MD5加密 - 空字符串")
    void testEncryptEmptyString() {
        String encrypted = MD5Util.encrypt("");
        
        assertNotNull(encrypted);
        assertEquals(32, encrypted.length());
    }

    @Test
    @DisplayName("测试MD5加密 - 特殊字符")
    void testEncryptSpecialCharacters() {
        String input = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
        String encrypted = MD5Util.encrypt(input);
        
        assertNotNull(encrypted);
        assertEquals(32, encrypted.length());
    }
}
