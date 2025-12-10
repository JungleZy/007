package com.nip.service;


import com.nip.common.constants.PostEnteringExerciseStatusEnum;
import com.nip.common.constants.PostEnteringExerciseTypeEnum;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostEnteringExerciseDao;
import com.nip.dao.PostEnteringExerciseWordStockDao;
import com.nip.dao.UserDao;
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

/**
 * @Author: wushilin
 * @Data: 2022-04-12 09:46
 * @Description:
 */
@ApplicationScoped
public class PostEnteringExerciseService {

  private final UserDao userDao;
  private final PostEnteringExerciseDao exerciseDao;
  private final PostEnteringExerciseWordStockDao wordStockDao;

  @Inject
  public PostEnteringExerciseService(UserDao userDao, PostEnteringExerciseDao exerciseDao, PostEnteringExerciseWordStockDao wordStockDao) {
    this.userDao = userDao;
    this.exerciseDao = exerciseDao;
    this.wordStockDao = wordStockDao;
  }

  @Transactional(rollbackOn = Exception.class)
  public PostEnteringExerciseVO add(PostEnteringExerciseAddParam addParam, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
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
    if (entity.getType().compareTo(PostEnteringExerciseTypeEnum.JYCZ.getCode()) == 0) {
      entity.setContent(wordStockDao.findByType(PostEnteringExerciseTypeEnum.JYCZ.getCode()).getContent());
    } else if (entity.getType().compareTo(PostEnteringExerciseTypeEnum.TZYY.getCode()) == 0) {
      entity.setContent(wordStockDao.findByType(PostEnteringExerciseTypeEnum.TZYY.getCode()).getContent());
    } else {
      //根据Type查询训练内容 content
      String content = wordStockDao.findByIdOptional(addParam.getWordId()).map(PostEnteringExerciseWordStockEntity::getContent)
          .orElseThrow(() -> new IllegalArgumentException("文章不存在！"));
      entity.setContent(content);
    }
    PostEnteringExerciseEntity save = exerciseDao.save(entity);
    return PojoUtils.convertOne(save, PostEnteringExerciseVO.class);
  }

  public List<PostEnteringExerciseVO> listPage(PostEnteringExercisePageParam param, String token) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    String sql;
    if (param.getType() == 0) {
      sql = "type > 2";
    } else if (param.getType() == 1) {
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
    return PojoUtils.convertOne(exerciseDao.findById(id), PostEnteringExerciseVO.class);
  }

  public boolean delete(String id) {

    return exerciseDao.deleteById(id);
  }

}
