package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.dto.sql.FindUserByRoleIdDto;
import com.nip.dto.sql.FindUserByStatusDescDto;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Set;

import static com.nip.common.constants.BaseConstants.*;

@ApplicationScoped
public class UserDao extends BaseRepository<UserEntity, String> {

  public List<FindUserByStatusDescDto> findUserInfoAllByStatusDesc() {
    return entityManager.createNamedQuery("find_user_by_status", FindUserByStatusDescDto.class).getResultList();
  }

  public List<UserEntity> findAllUser(List<String> ids) {
    if (ids.isEmpty()) {
      return findAll().list();
    } else {
      StringBuilder t = new StringBuilder();
      ids.forEach(s -> {
        t.append(s).append("|");
      });
      t.deleteCharAt(t.length() - 1);
      return find("id REGEXP :ids", Parameters.with("ids", t.toString())).list();
    }
  }

  public List<UserEntity> findUserEntitiesByUserNameStartingWith(String userName) {
    return find("userName like :userName", Parameters.with("userName", userName)).list();
  }

  @Transactional
  public UserEntity findUserEntityById(String id) {
    return find(ID, id).firstResult();
  }

  public UserEntity findUserEntityByIdCard(String idCard) {
    return find("idCard", idCard).firstResult();
  }

  public UserEntity findUserEntityByPassword(String password) {
    return find("password", password).firstResult();
  }

  public boolean existsUserEntitiesByIdCardOrUserAccount(String idCard, String userAccount) {
    return find("idCard = :idCard or userAccount = :userAccount",
        Parameters.with("idCard", idCard).and("userAccount", userAccount)
    ).count() > 0;
  }

  public boolean existsUserByTokenAndDeviceId(String token, String deviceId) {
    return
        find("token = :token and deviceId = :deviceId", Parameters.with(TOKEN, token).and(DEVICE_ID, deviceId)).count()
            > 0;
  }

  public UserEntity findUserEntityByUserAccount(String userAccount) {
    return find("userAccount", userAccount).firstResult();
  }

  public List<UserEntity> findAllByOrderByStatusDesc() {
    return findAll(Sort.by("status").descending()).list();
  }

  public List<UserEntity> findAllByStatus(int status) {
    return find("status", status).list();
  }

  public UserEntity findUserEntityByToken(String token) {
    return find(TOKEN, token).firstResult();
  }

  public UserEntity findFirstByDeviceId(String deviceId) {
    return find(DEVICE_ID, deviceId).firstResult();
  }

  public List<FindUserByRoleIdDto> findAllByRoleId(String role) {
    return entityManager.createNamedQuery("find_user_by_role_id", FindUserByRoleIdDto.class).setParameter(ID, role)
        .getResultList();
  }

  public boolean updateUser(UserEntity user) {
    try {
      update("token = ?1,deviceId = ?2 where id = ?3", user.getToken(), user.getDeviceId(), user.getId());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public List<UserEntity> findAllByUserNameLikeOrUserAccountLikeOrderByStatusDesc(String userName, String userAccount) {
    return find("userName like ?1 or userAccount like ?2", Sort.by("status").descending(), userName, userAccount).list();
  }

  public List<UserEntity> findAllByUserNameLikeOrderByStatusDesc(String userName) {
    return find("userName like ?1", Sort.by("status").descending(), userName).list();
  }

  public List<UserEntity> findAllByUserAccountLikeOrderByStatusDesc(String userName) {
    return find("userAccount like ?1", Sort.by("status").descending(), userName).list();
  }

  public List<UserEntity> queryByIdIn(Set<String> ids) {
    return find("id in ?1", ids).list();
  }

  public UserEntity findByUserAccount(String userAccount) {
    return find("userAccount", userAccount).firstResult();
  }
}
