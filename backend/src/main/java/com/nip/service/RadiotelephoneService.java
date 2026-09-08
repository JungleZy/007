package com.nip.service;

import com.nip.common.repository.IdempotentWrite;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.RadiotelephoneDao;
import com.nip.dao.UserDao;
import com.nip.dto.RadiotelephoneDto;
import com.nip.dto.vo.RadiotelephoneVO;
import com.nip.entity.RadiotelephoneEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 09:33
 * @Description:
 */
@ApplicationScoped
@Slf4j
public class RadiotelephoneService {

  private final UserDao userDao;
  private final RadiotelephoneDao radiotelephoneDao;
  private final IdempotentWrite idempotentWrite;

  @Inject
  public RadiotelephoneService(UserDao userDao, RadiotelephoneDao radiotelephoneDao,
                               IdempotentWrite idempotentWrite) {
    this.userDao = userDao;
    this.radiotelephoneDao = radiotelephoneDao;
    this.idempotentWrite = idempotentWrite;
  }

  @Transactional
  public List<RadiotelephoneVO> listPage(String token, RadiotelephoneDto dto) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    List<RadiotelephoneEntity> entityList = radiotelephoneDao.findAllByUserId(userEntity.getId());
    RadiotelephoneEntity byUserIdAndType = radiotelephoneDao.findByUserIdAndType(userEntity.getId(), dto.getType());
    if (byUserIdAndType == null) {
      // 读路径懒建：并发首调只能落 1 行（唯一键 uk_radiotelephone_train_user_type），撞键的一方复用对方那行
      entityList.add(accumulate(userEntity.getId(), dto.getType(), 0, 0));
    }
    List<RadiotelephoneVO> convert = PojoUtils.convert(entityList, RadiotelephoneVO.class);
    convert.sort(Comparator.comparingInt(RadiotelephoneVO::getType));
    return convert;
  }

  @Transactional(rollbackOn = Exception.class)
  public RadiotelephoneVO finish(RadiotelephoneDto dto, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    int increment = dto.getTotalTime() == null ? 0 : dto.getTotalTime();
    // 前端可以不经 listPage 直接结算：与 listPage 同口径走同一条幂等懒建路径，避免裸解引用 NPE
    RadiotelephoneEntity save = accumulate(userEntity.getId(), dto.getType(), 1, increment);
    return PojoUtils.convertOne(save, RadiotelephoneVO.class);
  }

  /**
   * 幂等懒建 + 累加 (user_id, type) 唯一行：查不到就建，查到就在其上累加。
   *
   * <p>整段读-改-写放在独立事务里执行（{@link IdempotentWrite#inNewTransaction}）。撞唯一键说明
   * 并发方刚插了同一行：只回滚那个独立事务，换新事务原样重跑一次，新快照能读到对方那行，
   * 于是走更新分支而不是再插一行。约束冲突之外的异常原样抛出，不吞。
   */
  private RadiotelephoneEntity accumulate(String userId, Integer type, int countDelta, int timeDelta) {
    // 两列都可空，而 MySQL 唯一索引允许多个 NULL 行 —— 懒建前必须挡住空键，否则唯一约束形同虚设
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("话报训练统计缺少用户标识");
    }
    if (type == null) {
      throw new IllegalArgumentException("话报训练统计缺少训练类型");
    }
    try {
      return idempotentWrite.inNewTransaction(() -> applyDelta(userId, type, countDelta, timeDelta));
    } catch (RuntimeException e) {
      if (!IdempotentWrite.isConstraintConflict(e)) {
        throw e;
      }
      log.info("话报训练统计行并发懒建撞唯一键，复用已存在行:userId={},type={}", userId, type);
      return idempotentWrite.inNewTransaction(() -> applyDelta(userId, type, countDelta, timeDelta));
    }
  }

  private RadiotelephoneEntity applyDelta(String userId, Integer type, int countDelta, int timeDelta) {
    RadiotelephoneEntity entity = radiotelephoneDao.findByUserIdAndType(userId, type);
    if (entity == null) {
      entity = new RadiotelephoneEntity();
      entity.setUserId(userId);
      entity.setType(type);
      entity.setTotalCount(0);
      entity.setTotalTime("0");
    }
    entity.setTotalCount((entity.getTotalCount() == null ? 0 : entity.getTotalCount()) + countDelta);
    entity.setTotalTime(String.valueOf(parseTotalTime(entity.getTotalTime()) + timeDelta));
    // 必须 flush：唯一键冲突要在独立事务内部抛出，才能被上面的重试逻辑接住
    return radiotelephoneDao.saveAndFlush(entity);
  }

  /** 历史数据里 totalTime 是字符串列，非数字视为 0，不让脏数据把结算打成 500 */
  private int parseTotalTime(String totalTime) {
    try {
      return Integer.parseInt(totalTime);
    } catch (NumberFormatException e) {
      log.warn("话报训练累计时长不是数字，按 0 计算:{}", totalTime);
      return 0;
    }
  }
}
