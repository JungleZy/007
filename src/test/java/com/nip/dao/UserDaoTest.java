package com.nip.dao;

import com.nip.entity.UserEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisplayName("UserDao数据访问层单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserDaoTest {

    @Inject
    UserDao userDao;

    private static String testUserId;

    @Test
    @Order(1)
    @TestTransaction
    @DisplayName("测试创建用户")
    void testCreateUser() {
        UserEntity user = new UserEntity();
        user.setWkno("TEST001");
        user.setPhone("13800138000");
        user.setEmail("test@example.com");
        user.setUserName("测试用户");
        user.setUserAccount("testuser");
        user.setUserSex(1);
        user.setStatus(1);

        userDao.persist(user);
        assertNotNull(user.getId());
        testUserId = user.getId();
    }

    @Test
    @Order(2)
    @TestTransaction
    @DisplayName("测试根据ID查询用户")
    void testFindUserEntityById() {
        if (testUserId == null) {
            return;
        }
        
        UserEntity user = userDao.findUserEntityById(testUserId);
        assertNotNull(user);
        assertEquals("TEST001", user.getWkno());
        assertEquals("testuser", user.getUserAccount());
    }

    @Test
    @Order(3)
    @TestTransaction
    @DisplayName("测试更新用户")
    void testUpdateUser() {
        if (testUserId == null) {
            return;
        }
        
        UserEntity user = userDao.findUserEntityById(testUserId);
        user.setUserName("更新后的用户名");
        userDao.persist(user);

        UserEntity updatedUser = userDao.findUserEntityById(testUserId);
        assertEquals("更新后的用户名", updatedUser.getUserName());
    }

    @Test
    @Order(4)
    @TestTransaction
    @DisplayName("测试删除用户")
    void testDeleteUser() {
        if (testUserId == null) {
            return;
        }
        
        userDao.deleteById(testUserId);
        UserEntity deletedUser = userDao.findUserEntityById(testUserId);
        assertNull(deletedUser);
    }

    @Test
    @DisplayName("测试查询不存在的用户")
    void testFindNonExistentUser() {
        UserEntity user = userDao.findUserEntityById("non-existent-id");
        assertNull(user);
    }

    @Test
    @DisplayName("测试根据身份证号查询用户")
    void testFindUserEntityByIdCard() {
        UserEntity user = userDao.findUserEntityByIdCard("non-existent-idcard");
        assertNull(user);
    }
}
