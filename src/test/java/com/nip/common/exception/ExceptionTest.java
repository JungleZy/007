package com.nip.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("自定义异常类单元测试")
class ExceptionTest {

    @Test
    @DisplayName("测试NIPException基本功能")
    void testNIPException() {
        String message = "测试异常消息";
        NIPException exception = new NIPException(message);

        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("测试NIPException带原因")
    void testNIPExceptionWithCause() {
        String message = "测试异常消息";
        Throwable cause = new RuntimeException("原始异常");
        NIPException exception = new NIPException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("测试DataRetrievalFailureException")
    void testDataRetrievalFailureException() {
        String message = "数据检索失败";
        DataRetrievalFailureException exception = new DataRetrievalFailureException(message);

        assertEquals(message, exception.getMessage());
        assertTrue(exception instanceof DataAccessException);
    }

    @Test
    @DisplayName("测试EmptyResultDataAccessException")
    void testEmptyResultDataAccessException() {
        String message = "查询结果为空";
        int expectedSize = 1;
        EmptyResultDataAccessException exception = new EmptyResultDataAccessException(message, expectedSize);

        assertTrue(exception.getMessage().contains(message));
        assertTrue(exception instanceof DataAccessException);
        assertEquals(expectedSize, exception.getExpectedSize());
        assertEquals(0, exception.getActualSize());
    }

    @Test
    @DisplayName("测试IncorrectResultSizeDataAccessException")
    void testIncorrectResultSizeDataAccessException() {
        String message = "结果大小不正确";
        int expectedSize = 1;
        int actualSize = 2;
        IncorrectResultSizeDataAccessException exception = new IncorrectResultSizeDataAccessException(message,
                expectedSize, actualSize);

        assertTrue(exception.getMessage().contains(message));
        assertTrue(exception instanceof DataAccessException);
        assertEquals(expectedSize, exception.getExpectedSize());
        assertEquals(actualSize, exception.getActualSize());
    }
}
