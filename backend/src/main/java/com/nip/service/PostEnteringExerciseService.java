package com.nip.service;


import com.nip.common.constants.PostEnteringExerciseStatusEnum;
import com.nip.common.constants.PostEnteringExerciseTypeEnum;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostEnteringExerciseDao;
import com.nip.dao.PostEnteringExerciseWordStockDao;
import com.nip.dto.vo.PostEnteringExerciseVO;
import com.nip.dto.vo.param.PostEnteringExerciseAddParam;
import com.nip.dto.vo.param.PostEnteringExerciseFinishParam;
import com.nip.dto.vo.param.PostEnteringExercisePageParam;
import com.nip.dto.vo.param.PostEnteringExerciseUpdateParam;
import com.nip.entity.PostEnteringExerciseEntity;
import com.nip.entity.PostEnteringExerciseWordStockEntity;
import com.nip.entity.UserEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:46
 * @Description:
 */
@ApplicationScoped
public class PostEnteringExerciseService {

  private final UserService userService;
  private final PostEnteringExerciseDao exerciseDao;
  private final PostEnteringExerciseWordStockDao wordStockDao;

  /** 属主判定的唯一口径（个人域 = 仅创建者）。 */
  @Inject TrainWriteAccess trainWriteAccess;

  @Inject
  public PostEnteringExerciseService(UserService userService, PostEnteringExerciseDao exerciseDao, PostEnteringExerciseWordStockDao wordStockDao) {
    this.userService = userService;
    this.exerciseDao = exerciseDao;
    this.wordStockDao = wordStockDao;
  }

  @Transactional(rollbackOn = Exception.class)
  public PostEnteringExerciseVO add(PostEnteringExerciseAddParam addParam, String token) {
    // DATA-03：走 userService.getUserByToken，token 失效时抛 UnauthorizedException（200+code203），不再裸解引用 NPE
    UserEntity userEntity = userService.getUserByToken(token);
    PostEnteringExerciseEntity entity = new PostEnteringExerciseEntity();
    entity.setCreateUserId(userEntity.getId());
    entity.setName(addParam.getName());
    entity.setType(addParam.getType());
    entity.setStatus(PostEnteringExerciseStatusEnum.NOT_STARTED.getStatus());
    entity.setSpeed(0);
    entity.setAccuracy(0.0);
    entity.setDuration(0);
    entity.setCorrectNum(0);
    entity.setErrorNum(0);
    //如果是军语则选则默认的军语文章
    // Phase 7.4：type 是可空 Integer，裸 compareTo 会拆箱 NPE
    if (entity.getType() == null) {
      throw new IllegalArgumentException("训练类型不能为空");
    }
    if (entity.getType().compareTo(PostEnteringExerciseTypeEnum.JYCZ.getCode()) == 0) {
      entity.setContent(defaultContentOf(PostEnteringExerciseTypeEnum.JYCZ));
    } else if (entity.getType().compareTo(PostEnteringExerciseTypeEnum.TZYY.getCode()) == 0) {
      entity.setContent(defaultContentOf(PostEnteringExerciseTypeEnum.TZYY));
    } else {
      //根据Type查询训练内容 content
      String content = wordStockDao.findByIdOptional(addParam.getWordId()).map(PostEnteringExerciseWordStockEntity::getContent)
          .orElseThrow(() -> new IllegalArgumentException("文章不存在！"));
      entity.setContent(content);
    }
    PostEnteringExerciseEntity save = exerciseDao.save(entity);
    return PojoUtils.convertOne(save, PostEnteringExerciseVO.class);
  }

  /**
   * 取军语类训练的默认文章内容。
   *
   * <p>{@code findByType} 走 {@code firstResult()}，词库里没有该 type 的行时返回 null；
   * 原先在这里直接 {@code .getContent()}，主数据没铺好就是一个 NPE 逸出成 500 堆栈，
   * 运维看不出是哪个类型的词库缺了。词库是主数据、只能靠补配置恢复，所以按参数错误
   * （{@code IllegalArgumentException} → 业务码 202）报出，文案里点名 type。
   *
   * <p>行存在但 {@code content} 为 null 同样不可用，{@code map} 会把它折成空 Optional 走同一条错误路径
   * —— 与 {@code add} 里按 id 取文章的 else 分支同口径。
   */
  private String defaultContentOf(PostEnteringExerciseTypeEnum type) {
    return Optional.ofNullable(wordStockDao.findByType(type.getCode()))
        .map(PostEnteringExerciseWordStockEntity::getContent)
        .orElseThrow(() -> new IllegalArgumentException(
            "未配置「" + type.getName() + "」类型（type=" + type.getCode() + "）的默认词库，请先在词库中添加该类型的文章"));
  }

  public List<PostEnteringExerciseVO> listPage(PostEnteringExercisePageParam param, String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    String sql;
    // Phase 7.4：type 是可空 Integer，裸 == 会拆箱 NPE；null 无法映射到三种查询口径，显式拒绝
    if (param.getType() == null) {
      throw new IllegalArgumentException("训练类型不能为空");
    }
    if (Objects.equals(param.getType(), 0)) {
      sql = "type > 2";
    } else if (Objects.equals(param.getType(), 1)) {
      sql = "type < 2";
    } else {
      sql = "type = 2";
    }
    List<PostEnteringExerciseEntity> entityPage = exerciseDao.find(sql + " and createUserId = ?1", Sort.by("createTime").descending(), userEntity.getId()).list();
    return PojoUtils.convert(entityPage, PostEnteringExerciseVO.class, (e, p) -> p.setContent(null));
  }

  @Transactional(rollbackOn = Exception.class)
  public void begin(PostEnteringExerciseUpdateParam param) {
    exerciseDao.begin(param.getId(), PostEnteringExerciseStatusEnum.UNDERWAY.getStatus());
  }

  @Transactional(rollbackOn = Exception.class)
  public void finish(PostEnteringExerciseFinishParam param) {
    exerciseDao.finish(param.getId(),
        PostEnteringExerciseStatusEnum.FINISH.getStatus(),
        param.getAccuracy(),
        param.getSpeed(),
        param.getDuration(),
        param.getContent(),
        param.getErrorNum(),
        param.getCorrectNum()
    );
  }

  public PostEnteringExerciseVO getById(String id) {
    // Phase 7.4：不存在的 id 原先返回一个全空 VO 空壳，改为显式报错
    PostEnteringExerciseEntity entity = exerciseDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    return PojoUtils.convertOne(entity, PostEnteringExerciseVO.class);
  }

  /**
   * 删除个人录入练习。属主字段是 {@code createUserId}（各域字段名不同，这里显式传入）。
   */
  @Transactional
  public boolean delete(String id, String token) {
    PostEnteringExerciseEntity entity = exerciseDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(userService.getUserByToken(token).getId(), entity.getCreateUserId(),
        "个人录入练习 " + id);
    return exerciseDao.deleteById(id);
  }

}
