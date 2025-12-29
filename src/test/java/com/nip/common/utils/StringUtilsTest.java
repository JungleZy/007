package com.nip.common.utils;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@DisplayName("StringUtils工具类单元测试")
class StringUtilsTest {

    @Test
    @DisplayName("测试isEmpty方法 - null值")
    void testIsEmptyWithNull() {
        assertTrue(StringUtils.isEmpty(null));
    }

    @Test
    @DisplayName("测试isEmpty方法 - 空字符串")
    void testIsEmptyWithEmptyString() {
        assertTrue(StringUtils.isEmpty(""));
    }

    @Test
    @DisplayName("测试isEmpty方法 - 非空字符串")
    void testIsEmptyWithNonEmptyString() {
        assertFalse(StringUtils.isEmpty("test"));
    }

    @Test
    @DisplayName("测试hasLength方法 - null值")
    void testHasLengthWithNull() {
        assertFalse(StringUtils.hasLength((String) null));
    }

    @Test
    @DisplayName("测试hasLength方法 - 空字符串")
    void testHasLengthWithEmptyString() {
        assertFalse(StringUtils.hasLength(""));
    }

    @Test
    @DisplayName("测试hasLength方法 - 非空字符串")
    void testHasLengthWithNonEmptyString() {
        assertTrue(StringUtils.hasLength("test"));
    }

    @Test
    @DisplayName("测试hasText方法 - null值")
    void testHasTextWithNull() {
        assertFalse(StringUtils.hasText((String) null));
    }

    @Test
    @DisplayName("测试hasText方法 - 空字符串")
    void testHasTextWithEmptyString() {
        assertFalse(StringUtils.hasText(""));
    }

    @Test
    @DisplayName("测试hasText方法 - 纯空格字符串")
    void testHasTextWithWhitespace() {
        assertFalse(StringUtils.hasText("   "));
    }

    @Test
    @DisplayName("测试hasText方法 - 包含文本的字符串")
    void testHasTextWithText() {
        assertTrue(StringUtils.hasText("  test  "));
    }
}
