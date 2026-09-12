package com.nip.service;

import com.nip.common.exception.UnauthorizedException;
import com.nip.common.constants.MessageConstants;
import com.nip.common.constants.ResponseCode;
import com.nip.common.response.Response;
import com.nip.common.response.ResponseResult;
import com.nip.common.security.PasswordHasher;
import com.nip.common.security.SessionToken;
import com.nip.common.utils.ToolUtil;
import com.nip.dao.RoleDao;
import com.nip.dao.UserDao;
import com.nip.dao.UserRoleDao;
import com.nip.dto.MenusDto;
import com.nip.dto.UserInfoDto;
import com.nip.dto.LoginSessionDto;
import com.nip.dto.UserProfile;
import com.nip.dto.general.UserSyncDto;
import com.nip.dto.UserSummary;
import com.nip.dto.sql.FindUserByRoleIdDto;
import com.nip.dto.sql.FindUserByStatusDescDto;
import com.nip.entity.RoleEntity;
import com.nip.entity.UserEntity;
import com.nip.entity.UserRoleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
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
  private final TransactionManager transactionManager;
  private final PasswordHasher passwordHasher;

  @Inject
  public UserService(UserDao userDao, RoleDao roleDao, UserRoleDao userRoleDao, MenusService menusService,
      TransactionManager transactionManager, PasswordHasher passwordHasher) {
    this.userDao = userDao;
    this.roleDao = roleDao;
    this.userRoleDao = userRoleDao;
    this.menusService = menusService;
    this.transactionManager = transactionManager;
    this.passwordHasher = passwordHasher;
  }

  /**
   * 根据用户ID获取用户实体对象
   * 此方法用于从数据库中检索指定ID的用户信息
   * 它依赖于UserDao接口的实现，该接口负责与数据库交互
   *
   * @param id 用户的唯一标识符，用于数据库查询
   * @return 不含密码和会话凭据的用户资料
   * @throws IllegalArgumentException 未查询到该用户时抛出（Phase 7.4：与 getUserAndRoleById 口径一致）
   */
  public UserProfile getUserById(String id) {
    return userDao.findByIdOptional(id)
        .map(UserProfile::from)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该用户"));
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
   * 根据用户名前缀获取用户目录条目
   * <p>
   * 此端点对所有已登录用户开放（选人、@提及等场景），因此只返回 {@link UserSummary}，
   * 不含 idCard/phone/email 等敏感字段。
   *
   * @param userName 用户名前缀，用于查询用户
   * @return 匹配用户的目录条目列表
   */
  public List<UserSummary> getUsersByUserNameStartingWith(String userName) {
    return userDao.findUserEntitiesByUserNameStartingWith(userName).stream().map(UserSummary::from).toList();
  }

  /**
   * 根据用户ID获取用户信息和角色信息
   *
   * @param id 用户ID，用于查询用户和角色信息
   * @return UserInfoDto对象，包含用户和角色信息
   */
  public UserInfoDto getUserAndRoleById(String id) {
    // Phase 7.4：不存在的用户 id 必须显式报错，否则下一行 getId() 直接 NPE 成 500
    UserEntity userEntity = Optional.ofNullable(userDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该用户"));
    RoleEntity role = roleDao.findRoleByUserId(userEntity.getId());
    UserInfoDto userInfoDto = new UserInfoDto();
    userInfoDto.setUser(UserProfile.from(userEntity));
    userInfoDto.setRole(role);
    return userInfoDto;
  }

  /**
   * 根据用户ID列表批量获取用户目录条目
   * <p>
   * 空列表显式返回空集（旧实现落到全表扫描）；非空列表走参数化的 {@code id in ?1}
   * （旧实现把 id 拼成 {@code REGEXP} 模式，元字符会改变匹配集）。
   * 该端点对所有已登录用户开放，故只返回 {@link UserSummary}。
   *
   * @param ids 用户ID列表，用于指定需要获取的用户
   * @return 所请求用户的目录条目列表
   */
  public List<UserSummary> getUsers(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return userDao.queryByIdIn(new LinkedHashSet<>(ids)).stream().map(UserSummary::from).toList();
  }

  /**
   * 获取所有用户实体列表
   * <p>
   * 此方法通过调用UserDao接口的findAllByOrderByStatusDesc方法来获取所有用户实体
   * 它按状态降序对用户进行排序，以便首先显示状态较高的用户
   *
   * @return 用户脱敏资料列表
   */
  public List<UserProfile> getAllUser() {
    return userDao.findAllByOrderByStatusDesc().stream().map(UserProfile::from).toList();
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

  public List<UserSummary> getUserDirectory() {
    return userDao.findAllByOrderByStatusDesc().stream().map(UserSummary::from).toList();
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
   * 创建新用户的公开注册入口。
   *
   * <p>注册请求不能根据客户端提供的 ID 进入管理更新路径；ID 由持久层生成。
   *
   * @param entity 注册信息
   * @return 注册结果
   */
  @Transactional
  public Response<Object> registerUser(UserEntity entity) {
    // null and the explicitly empty string mean "create"; every other supplied ID,
    // including whitespace, is a client attempt to select an existing row.
    if (entity == null || (entity.getId() != null && !entity.getId().isEmpty())) {
      return ResponseResult.error(ResponseCode.PARAMS_ERROR);
    }

    // Only registration-form fields may cross the public boundary.
    UserEntity registration = new UserEntity();
    registration.setUserAccount(entity.getUserAccount());
    registration.setUserName(entity.getUserName());
    registration.setIdCard(entity.getIdCard());
    registration.setPassword(entity.getPassword());
    registration.setUserSex(entity.getUserSex());
    registration.setUserImg(entity.getUserImg());
    registration.setEday(entity.getEday());

    Response<Object> response = addUser(registration, true);
    if (response.getCode() == ResponseCode.SUCCESS.getCode() && response.getData() instanceof UserEntity saved) {
      response.setData(new UserSummary(saved.getId(), saved.getUserName(), saved.getUserAccount(), saved.getUserImg()));
    }
    return response;
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
    entity.setPassword(passwordHasher.hash(entity.getPassword()));
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
    if (defaultRole == null) {
      // 静默无角色比失败更糟：新用户登录时 findRoleByUserId 会直接抛 NoResultException
      log.warn("库中不存在默认角色（isDefault=0），无法为用户 {} 分配角色", entity.getId());
      throw new IllegalStateException("系统未配置默认角色，无法创建用户");
    }
    UserRoleEntity userRoleEntity = new UserRoleEntity();
    userRoleEntity.setUserId(entity.getId());
    userRoleEntity.setRoleId(defaultRole.getId());
    userRoleDao.save(userRoleEntity);
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
    UserEntity existingUser = Optional.ofNullable(userDao.findById(entity.getId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该用户"));
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
      // 会话凭据一律由服务端签发：客户端在导入体里捎带的 token/deviceId 不得落库，
      // 否则导入方可以给任意新账号预置一枚自己知道的令牌，直接拿到该账号的会话。
      entity.setToken(null);
      entity.setDeviceId(null);
      entity.setPassword(passwordHasher.hash(entity.getPassword()));
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
      try {
        transactionManager.setRollbackOnly();
      } catch (SystemException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
        throw new IllegalStateException("无法标记用户角色保存事务回滚", e);
      }
      log.error("addUserRole error", e);
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
  public Response<LoginSessionDto> login(String userAccount, String password, String deviceId) {
    try {
      UserEntity user;
      user = userDao.findUserEntityByUserAccount(userAccount);
      if (null == user) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_USERACCOUNT_ERROR);
      }
      PasswordHasher.Verification passwordVerification = passwordHasher.verify(password, user.getPassword());
      if (!passwordVerification.matches()) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_PASSWORD_ERROR);
      }
      if (Objects.equals(user.getStatus(), 1)) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_IN_REVIEW);
      }
      if (Objects.equals(user.getStatus(), -1)) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.LOGIN_IN_DISABLE);
      }
      // Phase 7.4：status 为可空 Integer，裸拆箱会 NPE 后被兜底降级；null 归入「状态异常」分支
      if (!Objects.equals(user.getStatus(), 0)) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.DATA_EXCEPTION);
      }
      if (passwordVerification.needsUpgrade()) {
        user.setPassword(passwordHasher.hash(password));
        userDao.save(user);
      }

      // 令牌是不透明随机串（与账号/口令/设备号无关）：明文只随本次登录响应回给登录者本人，
      // 库里只落摘要，任何一次库导出或日志泄露都还原不出凭据。
      String token = SessionToken.issue();
      user.setToken(SessionToken.hash(token));
      user.setDeviceId(deviceId);
      if (!userDao.updateUser(user)) {
        return ResponseResult.error(ResponseCode.SYSTEM_ERROR, MessageConstants.DATA_EXCEPTION);
      }
      RoleEntity role = roleDao.findRoleByUserId(user.getId());
      List<MenusDto> menusDtoList = role.getIsAdmin() == 0
          ? menusService.getMenusDtos()
          : menusService.getMenusDtosById(role.getId());
      LoginSessionDto loginSession = new LoginSessionDto(UserProfile.from(user), role, menusDtoList, token, deviceId);
      return ResponseResult.success(MessageConstants.LOGIN_SUCCESS, loginSession);
    } catch (Exception e) {
      try {
        transactionManager.setRollbackOnly();
      } catch (SystemException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
        throw new IllegalStateException("无法标记登录事务回滚", e);
      }
      log.error("login error", e);
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
   * @param token        当前会话令牌，只修改令牌所属用户
   * @param oldPassword  用户当前的密码，用于验证身份
   * @param newPassword  用户的新密码，用于替换旧密码
   * @param newPasswordV 新密码的验证值，确保用户正确输入新密码
   * @return 返回一个Response对象，包含操作结果的布尔值
   */
  @Transactional
  public Response<Boolean> changePassword(String token, String oldPassword, String newPassword, String newPasswordV) {
    UserEntity user = getUserByToken(token);
    try {
      if (!passwordHasher.verify(oldPassword, user.getPassword()).matches()) {
        return ResponseResult.success(MessageConstants.PASSWORD_NOW_ERROR, false);
      }
      if (!newPassword.equals(newPasswordV)) {
        return ResponseResult.success(MessageConstants.PASSWORD_INCONFORMITY, false);
      }
      user.setPassword(passwordHasher.hash(newPassword));
      userDao.save(user);
      return ResponseResult.success(MessageConstants.DATA_SUCCESS, true);
    } catch (IllegalArgumentException | IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      try {
        transactionManager.setRollbackOnly();
      } catch (SystemException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
        throw new IllegalStateException("无法标记密码修改事务回滚", e);
      }
      log.error("changePassword", e);
      return ResponseResult.success(MessageConstants.DATA_EXCEPTION, false);
    }
  }

  /** 验证当前会话所属用户的密码，不按摘要扫描其他用户。 */
  public Response<Boolean> verifyPassword(String token, String password) {
    UserEntity user = getUserByToken(token);
    return ResponseResult.success(MessageConstants.DATA_SUCCESS,
        passwordHasher.verify(password, user.getPassword()).matches());
  }

  /**
   * 根据用户令牌获取用户实体
   *
   * @param token 用户令牌，用于唯一标识用户会话
   * @return UserEntity 用户实体对象，查无用户（token 无效或已过期）时抛 UnauthorizedException（映射为 200+code203 信封）
   */
  public UserEntity getUserByToken(String token) {
    UserEntity user = userDao.findUserEntityByToken(token);
    if (user == null) {
      throw new UnauthorizedException("token 无效或已过期");
    }
    return user;
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
   * 按账号对齐离线导入包里的用户行，并返回「包内原始 id → 本库 id」的映射。
   *
   * <p><b>字段白名单（SEC-05）</b>：导入包来自客户端，整包字段都不可信，因此新建账号
   * <b>只</b>落 {@code userAccount}/{@code userName}/{@code userImg} 三项业务标识，其余一律服务端决定：
   * <ul>
   *   <li>{@code token}/{@code deviceId} 留 NULL —— 导入绝不产生可用会话凭证；</li>
   *   <li>{@code password} 留 NULL（{@code t_user.password} 允许 NULL）。
   *       {@code PasswordHasher.verify} 对 {@code stored == null} 恒不匹配，故该账号无法登录，
   *       须管理员 {@link #resetPassword(String)} 后才可用；此处<b>不</b>生成随机口令或默认口令；</li>
   *   <li>{@code status} 固定为 0（正常），与 {@code signin} 的新建口径一致 ——
   *       不接受包内 {@code status}，否则客户端可借导入直接决定账号状态。</li>
   * </ul>
   * 注意这里不能再用 {@code PojoUtils.convertOne(item, UserEntity.class)} 按名整体拷贝：
   * 那是「DTO 恰好没有凭据字段」的隐式安全，DTO 一加字段就静默回归。
   *
   * @param users 导入包里的用户行
   * @return 键为包内原始 id，值为本库 id
   */
  @Transactional
  public Map<String, String> replaceUserIdAndSaveIfNotExist(List<UserSyncDto> users) {
    Map<String, String> mp = new HashMap<>();
    if (users == null) {
      return mp;
    }
    for (UserSyncDto item : users) {
      UserEntity byUserAccount = userDao.findByUserAccount(item.getUserAccount());
      if (byUserAccount != null) {
        mp.put(item.getId(), byUserAccount.getId());
        continue;
      }
      // 原 id 必须在建号前取出：旧实现先 item.setId(null) 再 mp.put(item.getId(), ...)，
      // 于是所有新建用户都挤在 key=null 的一格里，导入侧 userIdMap.get(原 id) 恒为 null ——
      // 训练 createUser 与全部参训/报底行的 userId 被写成 null，导入整体失效。
      String sourceId = item.getId();
      UserEntity created = new UserEntity();
      created.setUserAccount(item.getUserAccount());
      created.setUserName(item.getUserName());
      created.setUserImg(item.getUserImg());
      created.setStatus(0);
      setDefaultAvatarIfNull(created);
      userDao.saveAndFlush(created);
      mp.put(sourceId, created.getId());
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
    UserEntity user = Optional.ofNullable(userDao.findById(userId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该用户"));

    String temporaryPassword = "123456";
    user.setPassword(passwordHasher.hash(temporaryPassword));
    userDao.save(user);
    return temporaryPassword;
  }
}
