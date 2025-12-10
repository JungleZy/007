package com.nip.service;

import com.nip.common.constants.MessageConstants;
import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.utils.AESUtil;
import com.nip.common.utils.MD5Util;
import com.nip.common.utils.PojoUtils;
import com.nip.common.utils.ToolUtil;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dto.MenusDto;
import com.nip.dto.UserInfoDto;
import com.nip.dto.general.UserSyncDto;
import com.nip.dto.sql.FindUserByRoleIdDto;
import com.nip.dto.sql.FindUserByStatusDescDto;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

import static com.nip.common.constants.BaseConstants.USER_ID;

@Slf4j
@ApplicationScoped
public class UserService {
  private final UserDao userDao;
  private final RoleDao roleDao;
  private final UserRoleDao userRoleDao;
  private final MenusService menusService;

  @Inject
  public UserService(UserDao userDao, RoleDao roleDao, UserRoleDao userRoleDao, MenusService menusService) {
    this.userDao = userDao;
    this.roleDao = roleDao;
    this.userRoleDao = userRoleDao;
    this.menusService = menusService;
  }

  /**
   * 根据用户ID获取用户实体对象
   * 此方法用于从数据库中检索指定ID的用户信息
   * 它依赖于UserDao接口的实现，该接口负责与数据库交互
   *
   * @param id 用户的唯一标识符，用于数据库查询
   * @return UserEntity对象，包含查询到的用户信息如果没有找到对应的用户，将返回null
   */
  public UserEntity getUserById(String id) {
    return userDao.findById(id);
  }

  /**
   * 根据用户ID获取用户实体对象
   * 此方法用于从数据库中检索指定ID的用户信息，并返回用户实体对象
   * 它封装了数据访问层的方法调用，为上层提供数据支持
   *
   * @param id 用户ID，用于唯一标识用户
   * @return UserEntity 返回用户实体对象，包含用户详细信息如果找不到对应的用户，则返回null
   */
  public UserEntity getUserByIdNew(String id) {
    return userDao.findUserEntityById(id);
  }

  /**
   * 根据用户名前缀获取用户列表
   * <p>
   * 此方法用于查询用户名以特定前缀开始的用户实体列表通过调用UserDao中的相应方法来实现
   *
   * @param userName 用户名前缀，用于查询用户
   * @return 包含用户名以前缀开始的用户实体列表
   */
  public List<UserEntity> getUsersByUserNameStartingWith(String userName) {
    return userDao.findUserEntitiesByUserNameStartingWith(userName);
  }

  /**
   * 根据用户ID获取用户信息和角色信息
   *
   * @param id 用户ID，用于查询用户和角色信息
   * @return UserInfoDto对象，包含用户和角色信息
   */
  public UserInfoDto getUserAndRoleById(String id) {
    UserEntity userEntity = userDao.findById(id);
    RoleEntity role = roleDao.findRoleByUserId(userEntity.getId());
    UserInfoDto userInfoDto = new UserInfoDto();
    userInfoDto.setUser(userEntity);
    userInfoDto.setRole(role);
    return userInfoDto;
  }

  /**
   * 根据用户ID列表获取用户实体列表
   *
   * @param ids 用户ID列表，用于指定需要获取的用户实体
   * @return 返回一个UserEntity对象列表，包含所请求的用户信息
   */
  public List<UserEntity> getUsers(List<String> ids) {
    return userDao.findAllUser(ids);
  }

  /**
   * 获取所有用户实体列表
   * <p>
   * 此方法通过调用UserDao接口的findAllByOrderByStatusDesc方法来获取所有用户实体
   * 它按状态降序对用户进行排序，以便首先显示状态较高的用户
   *
   * @return 返回一个UserEntity对象列表，包含所有用户实体
   */
  public List<UserEntity> getAllUser() {
    return userDao.findAllByOrderByStatusDesc();
  }

  /**
   * 根据用户状态获取所有用户信息
   * <p>
   * 此方法用于从用户状态的角度获取所有相关用户信息它调用了用户数据访问对象中的相应方法
   * 主要用于需要根据用户状态获取详细用户信息的场景
   *
   * @return 包含用户信息的列表，按用户状态降序排列如果无数据，则返回空列表
   */
  public List<FindUserByStatusDescDto> getUserInfoAllByStatusDesc() {
    return userDao.findUserInfoAllByStatusDesc();
  }

  /**
   * 添加或更新用户信息
   * <p>
   * 此方法根据用户实体和类型参数，处理用户信息的添加或更新请求如果用户ID无效，则调用处理无效ID的方法
   * 如果用户账户格式不正确，则返回错误信息接着，从身份证中提取出生日期，如果身份证格式错误则返回相应的错误信息
   * 最后，根据用户ID是否为空，决定是处理新用户还是更新现有用户的信息
   *
   * @param entity 用户实体，包含用户的相关信息
   * @param type   指示是否为新用户添加操作的布尔值
   * @return 返回包含处理结果的响应对象
   */
  @Transactional
  public Response<Object> addUser(UserEntity entity, boolean type) {
    if (isInvalidId(entity.getId())) {
      handleInvalidId(entity);
    }

    if (!isValidAccount(entity.getUserAccount())) {
      return ResponseResult.error("账户格式不正确，最少4位最多32位，只能是数字或字母或汉字");
    }

    String bDay = getBirthDayFromIdCard(entity.getIdCard());
    if (bDay == null) {
      return ResponseResult.error(MessageConstants.IDCARD_FORMAT_ERROR);
    }

    if (StringUtils.isEmpty(entity.getId())) {
      return handleNewUser(entity, bDay, type);
    } else {
      return handleExistingUser(entity, bDay);
    }
  }

  /**
   * 检查给定的ID是否是无效的字符串ID
   * 无效的定义是ID不为null但为空字符串
   *
   * @param id 待检查的字符串ID
   * @return 如果ID不为null且为空字符串，则返回true，表示ID无效；否则返回false
   */
  private boolean isInvalidId(String id) {
    return id != null && id.isEmpty();
  }

  /**
   * 处理无效的用户ID
   * 当用户实体的ID被视为无效时，调用此方法将ID设置为null，以确保数据的一致性
   *
   * @param entity 用户实体对象，其ID需要被设置为无效
   * @return 返回null，表示不返回任何响应对象
   */
  private Response<Object> handleInvalidId(UserEntity entity) {
    entity.setId(null);
    return null;
  }

  /**
   * 检查用户账号是否有效
   * 有效性规则是账号只能包含字母、数字和汉字，长度为4到32个字符
   *
   * @param userAccount 用户输入的账号字符串
   * @return 如果账号符合规则，则返回true；否则返回false
   */
  private boolean isValidAccount(String userAccount) {
    return Pattern.matches("[A-Za-z0-9\u4300-\u9fa5]{4,32}", userAccount);
  }

  /**
   * 从身份证号码中获取生日
   *
   * @param idCard 身份证号码
   * @return 生日信息，如果身份证号码无效或解析失败，将返回空字符串
   */
  private String getBirthDayFromIdCard(String idCard) {
    return ToolUtil.handleIdCard(idCard);
  }

  /**
   * 处理新用户注册请求
   *
   * @param entity 用户实体，包含用户的基本信息
   * @param bDay   用户的生日
   * @param type   标志是否为新用户分配默认角色
   * @return 返回一个包含处理结果的Response对象
   */
  private Response<Object> handleNewUser(UserEntity entity, String bDay, boolean type) {
    UserEntity existingUser = userDao.findUserEntityByUserAccount(entity.getUserAccount());
    if (existingUser != null) {
      return ResponseResult.error(MessageConstants.USER_ACCOUNT_REPEAT);
    }
    entity.setPassword(MD5Util.encrypt(entity.getPassword()));
    entity.setBday(bDay);
    entity.setStatus(0);
    setDefaultAvatarIfNull(entity);
    UserEntity save = userDao.save(entity);
    if (type) {
      assignDefaultRole(save);
    }
    return ResponseResult.success(save);
  }

  /**
   * 如果用户实体的头像为空，则设置默认头像
   * 此方法旨在确保所有用户都有一个头像，无论是上传的还是默认的
   *
   * @param entity 用户实体，代表待检查和设置默认头像的用户
   */
  private void setDefaultAvatarIfNull(UserEntity entity) {
    if (Objects.isNull(entity.getUserImg())) {
      entity.setUserImg("/userImages/pag-avatar-man.png");
    }
  }

  /**
   * 为用户分配默认角色
   * 在用户创建过程中调用此方法，以确保每个用户至少有一个默认角色
   *
   * @param entity 用户实体，代表新创建的用户
   */
  private void assignDefaultRole(UserEntity entity) {
    RoleEntity defaultRole = roleDao.find("isDefault", 0).firstResult();
    if (defaultRole != null) {
      UserRoleEntity userRoleEntity = new UserRoleEntity();
      userRoleEntity.setUserId(entity.getId());
      userRoleEntity.setRoleId(defaultRole.getId());
      userRoleDao.save(userRoleEntity);
    }
  }

  /**
   * 处理现有用户信息
   * 当用户信息已存在时，根据新提供的实体信息和生日进行更新
   *
   * @param entity 用户实体对象，包含用户的相关信息
   * @param bDay   用户的生日，可能用于特定的逻辑处理
   * @return 返回一个包含更新后用户信息的响应对象
   */
  private Response<Object> handleExistingUser(UserEntity entity, String bDay) {
    UserEntity existingUser = userDao.findById(entity.getId());
    updateExistingUser(existingUser, entity, bDay);
    setDefaultAvatarIfNull(existingUser);
    return ResponseResult.success(existingUser);
  }

  /**
   * 更新现有用户的信息
   * <p>
   * 此方法用于将现有用户实体的属性更新为新提供的值
   * 它不仅更新用户的账户信息，还包括用户的生日
   *
   * @param existingUser  现有用户的实体，其信息需要被更新
   * @param updatedEntity 包含最新信息的用户实体，用于更新现有用户
   * @param bDay          用户的新生日信息，以字符串形式提供
   */
  private void updateExistingUser(UserEntity existingUser, UserEntity updatedEntity, String bDay) {
    existingUser.setUserName(updatedEntity.getUserName());
    existingUser.setUserAccount(updatedEntity.getUserAccount());
    existingUser.setIdCard(updatedEntity.getIdCard());
    existingUser.setUserSex(updatedEntity.getUserSex());
    existingUser.setEday(updatedEntity.getEday());
    existingUser.setBday(bDay);
  }

  /**
   * 导入用户信息
   * 此方法用于批量导入用户实体在导入过程中，会检查用户是否已存在如果用户不存在，则加密用户密码并保存
   * 如果用户已存在，则将其添加到返回列表中此外，还会为新用户分配默认角色
   *
   * @param entitys 用户实体列表，包含待导入的用户信息
   * @return 返回已存在用户和未导入用户的列表
   */
  @Transactional
  public List<UserEntity> importUser(List<UserEntity> entitys) {
    List<UserEntity> list = new ArrayList<>();
    for (UserEntity entity : entitys) {
      boolean b = userDao.existsUserEntitiesByIdCardOrUserAccount(entity.getIdCard(), entity.getUserAccount());
      if (b) {
        list.add(entity);
        continue;
      }
      entity.setPassword(MD5Util.encrypt(entity.getPassword()));
      UserEntity save = userDao.save(entity);
      List<RoleEntity> allByIsDefault = roleDao.find("isDefault", 0).list();
      if (!allByIsDefault.isEmpty()) {
        UserRoleEntity userRoleEntity = new UserRoleEntity();
        userRoleEntity.setUserId(save.getId());
        userRoleEntity.setRoleId(allByIsDefault.getFirst().getId());
        userRoleDao.save(userRoleEntity);
      }
    }
    return list;
  }

  /**
   * 添加用户角色关联
   *
   * @param userId  用户ID，不能为空
   * @param roleIds 角色ID列表，不能为空
   * @return boolean 表示操作是否成功，成功返回true，否则返回false
   */
  @Transactional
  public boolean addUserRole(String userId, List<String> roleIds) {
    try {
      if (StringUtils.isEmpty(userId) || roleIds.isEmpty()) {
        return true;
      }
      userRoleDao.delete(USER_ID, userId);
      for (String roleId : roleIds) {
        UserRoleEntity userRoleEntity = new UserRoleEntity();
        userRoleEntity.setUserId(userId);
        userRoleEntity.setRoleId(roleId);
        userRoleDao.save(userRoleEntity);
      }
      return true;
    } catch (Exception e) {
      log.error("addUserRole error:{}", e.getMessage());
      return false;
    }
  }

  /**
   * 用户登录方法
   *
   * @param userAccount 用户账号，用于查询用户信息
   * @param password    用户密码，需要进行加密处理后与数据库中密码进行对比
   * @param deviceId    设备ID，用于绑定用户设备信息
   * @return 返回登录结果，包括用户信息DTO
   */
  @Transactional
  public Response<UserInfoDto> login(String userAccount, String password, String deviceId) {
    try {
      UserEntity user;
      user = userDao.findUserEntityByUserAccount(userAccount);
      if (null == user) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_USERACCOUNT_ERROR);
      }
      if (!MD5Util.encrypt(password).equals(user.getPassword())) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_PASSWORD_ERROR);
      }
      if (user.getStatus() == 1) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_IN_REVIEW);
      }
      if (user.getStatus() == -1) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_IN_DISABLE);
      }
      if (user.getStatus() != 0) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.DATA_EXCEPTION);
      }

      String token = AESUtil.encrypt(userAccount + "-" + password + "-" + deviceId, AESUtil.UKDAI_AES_KEY);
      user.setToken(token);
      user.setDeviceId(deviceId);
      if (!userDao.updateUser(user)) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.DATA_EXCEPTION);
      }
      UserInfoDto userInfoDto = new UserInfoDto();
      RoleEntity role = roleDao.findRoleByUserId(user.getId());
      List<MenusDto> menusDtoList = role.getIsAdmin() == 0
          ? menusService.getMenusDtos()
          : menusService.getMenusDtosById(role.getId());
      userInfoDto.setUser(user);
      userInfoDto.setRole(role);
      userInfoDto.setMenus(menusDtoList);
      return ResponseResult.success(MessageConstants.LOGIN_SUCCESS, userInfoDto);
    } catch (Exception e) {
      log.error("login error:{}", e.getMessage());
      return ResponseResult.error(ResponseCode.SYSTEM_ERROR.getCode(), MessageConstants.DATA_EXCEPTION);
    }
  }

  /**
   * 用户退出功能
   * 通过使用户实体与给定令牌关联的设备ID和令牌本身无效来实现用户退出
   *
   * @param token 用户登录时生成的唯一令牌
   * @return 如果找到对应的用户并成功更新，则返回true；否则返回false
   */
  @Transactional
  public Boolean userOut(String token) {
    UserEntity user = userDao.findUserEntityByToken(token);
    if (null != user) {
      user.setToken(null);
      user.setDeviceId(null);
      userDao.updateUser(user);
      return true;
    } else {
      return false;
    }
  }

  /**
   * 修改用户密码
   *
   * @param id           用户ID，用于定位需要修改密码的用户
   * @param oldPassword  用户当前的密码，用于验证身份
   * @param newPassword  用户的新密码，用于替换旧密码
   * @param newPasswordV 新密码的验证值，确保用户正确输入新密码
   * @return 返回一个Response对象，包含操作结果的布尔值
   */
  @Transactional
  public Response<Boolean> changePassword(String id, String oldPassword, String newPassword, String newPasswordV) {
    try {
      UserEntity user = userDao.findById(id);

      if (!MD5Util.encrypt(oldPassword).equals(user.getPassword())) {
        return ResponseResult.success(MessageConstants.PASSWORD_NOW_ERROR, false);
      }

      if (!newPassword.equals(newPasswordV)) {
        return ResponseResult.success(MessageConstants.PASSWORD_INCONFORMITY, false);
      }

      user.setPassword(MD5Util.encrypt(newPassword));
      userDao.save(user);

      return ResponseResult.success(MessageConstants.DATA_SUCCESS, true);
    } catch (Exception e) {
      return ResponseResult.success(MessageConstants.DATA_EXCEPTION, false);
    }
  }

  /**
   * 验证密码是否存在的方法
   * <p>
   * 该方法接收一个明文密码作为输入，使用MD5加密后，查询数据库中是否存在对应的密文密码
   * 如果存在则返回成功，否则返回失败
   *
   * @param password 明文密码
   * @return Response<Object> 包含是否存在该密码的响应对象
   */
  public Response<Object> verifyPassword(String password) {
    try {
      boolean exists = userDao.findUserEntityByPassword(MD5Util.encrypt(password)) != null;
      return ResponseResult.success(MessageConstants.DATA_SUCCESS, exists);
    } catch (Exception e) {
      return ResponseResult.error(MessageConstants.DATA_EXCEPTION);
    }
  }

  /**
   * 根据用户令牌获取用户实体
   *
   * @param token 用户令牌，用于唯一标识用户会话
   * @return UserEntity 返回用户实体对象，如果找不到则返回null
   */
  public UserEntity getUserByToken(String token) {
    return userDao.findUserEntityByToken(token);
  }

  /**
   * 获取所有教师用户信息
   * <p>
   * 教师在系统中的角色ID为"1"此方法通过调用UserDao的findAllByRoleId方法，
   * 并传入角色ID"1"来获取所有教师用户的详细信息
   *
   * @return List<FindUserByRoleIdDto> 包含所有教师用户信息的列表
   */
  public List<FindUserByRoleIdDto> findAllTeacher() {
    return userDao.findAllByRoleId("1");
  }

  /**
   * 获取所有学生用户信息
   * <p>
   * 该方法用于从用户角色ID为"2"的所有用户中，查询并返回这些用户的信息
   * 主要目的是为了提供一个接口，以便在不需要具体角色信息的情况下，也能获取到所有学生用户的信息
   *
   * @return 包含所有学生用户信息的列表，每个用户信息以FindUserByRoleIdDto对象表示
   */
  public List<FindUserByRoleIdDto> findAllStu() {
    return userDao.findAllByRoleId("2");
  }

  /**
   * 根据用户名和用户账号查询用户列表
   *
   * @param userName    用户名，用于模糊查询
   * @param userAccount 用户账号，用于模糊查询
   * @return 返回一个Response对象，包含用户实体列表
   * <p>
   * 此方法根据提供的用户名和用户账号参数，通过用户数据访问对象（userDao）查询匹配的用户列表
   * 如果两个参数都提供，则使用两个参数进行模糊查询；如果只提供其中一个参数，则只使用该参数查询；
   * 如果两个参数都没有提供，则返回按照状态降序排列的所有用户列表
   * <p>
   * 注意：此方法包含异常处理，以处理可能发生的数据库查询异常
   */
  public Response<List<UserEntity>> getAllUserByContent(String userName, String userAccount) {
    try {
      if (StringUtils.isNotEmpty(userName) && StringUtils.isNotEmpty(userAccount)) {
        List<UserEntity> list = userDao.findAllByUserNameLikeOrUserAccountLikeOrderByStatusDesc("%" + userName + "%", "%" + userAccount + "%");
        return ResponseResult.success(list);
      } else if (StringUtils.isNotEmpty(userName) && StringUtils.isEmpty(userAccount)) {
        List<UserEntity> list = userDao.findAllByUserNameLikeOrderByStatusDesc("%" + userName + "%");
        return ResponseResult.success(list);
      } else if (StringUtils.isNotEmpty(userAccount) && StringUtils.isEmpty(userName)) {
        List<UserEntity> list = userDao.findAllByUserAccountLikeOrderByStatusDesc("%" + userAccount + "%");
        return ResponseResult.success(list);
      } else {
        return ResponseResult.success(userDao.findAllByOrderByStatusDesc());
      }
    } catch (Exception e) {
      return ResponseResult.error(MessageConstants.DATA_EXCEPTION);
    }
  }

  /**
   * 根据用户列表替换用户ID，并在不存在时保存用户信息
   * 此方法用于处理用户同步时的用户ID分配问题如果用户不存在，会创建新用户并分配新ID；
   * 如果用户已存在，则使用现有用户的ID此方法返回一个映射，用于记录原始用户ID与实际保存的用户ID之间的对应关系
   *
   * @param users 用户同步数据列表，包含需要同步的用户信息
   * @return 返回一个映射，键为原始用户ID，值为实际保存的用户ID
   */
  public Map<String, String> replaceUserIdAndSaveIfNotExist(List<UserSyncDto> users) {
    Map<String, String> mp = new HashMap<>();
    for (UserSyncDto item : users) {
      UserEntity byUserAccount = userDao.findByUserAccount(item.getUserAccount());
      if (byUserAccount == null) {
        item.setId(null);
        UserEntity userEntity = PojoUtils.convertOne(item, UserEntity.class);
        userDao.saveAndFlush(userEntity);
        mp.put(item.getId(), userEntity.getId());
      } else {
        mp.put(item.getId(), byUserAccount.getId());
      }
    }
    return mp;
  }
  @Transactional
  public Boolean delete(String userId){
    userRoleDao.delete("userId",userId);
    return userDao.deleteById(userId);
  }
  @Transactional
  public String resetPassword(String userId){
    UserEntity user = userDao.findById(userId);

    user.setPassword(MD5Util.encrypt("123456"));
    userDao.save(user);
    return "123456";
  }
}
