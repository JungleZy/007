package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostRadiotelephoneTrainStatusEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostRadiotelephoneDao;
import com.nip.dao.RadiotelephoneTermDataDao;
import com.nip.dao.UserDao;
import com.nip.dto.PostRadiotelephoneDto;
import com.nip.dto.vo.PostRadiotelephoneVO;
import com.nip.entity.PostRadiotelephoneTermDataEntity;
import com.nip.entity.PostRadiotelephoneTrainEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 17:44
 * @Description:
 */
@ApplicationScoped
public class PostRadiotelephoneService {

  private final PostRadiotelephoneDao dao;
  private final RadiotelephoneTermDataDao dataDao;
  private final UserDao userDao;

  @Inject
  public PostRadiotelephoneService(PostRadiotelephoneDao dao, RadiotelephoneTermDataDao dataDao, UserDao userDao) {
    this.dao = dao;
    this.dataDao = dataDao;
    this.userDao = userDao;
  }

  private final Random random = new Random();

  @Transactional
  public PostRadiotelephoneVO add(String token, PostRadiotelephoneDto dto) {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    List<PostRadiotelephoneTermDataEntity> entityList = dataDao.findByTypeOrderByKey(dto.getType());
    List<PostRadiotelephoneTermDataEntity> contentList = new ArrayList<>();
    for (int i = 0; i < dto.getNumber(); i++) {
      int index = random.nextInt(entityList.size() - 1);
      PostRadiotelephoneTermDataEntity entity = entityList.get(index);
      contentList.add(entity);
    }
    PostRadiotelephoneTrainEntity entity = PojoUtils.convertOne(dto, PostRadiotelephoneTrainEntity.class, (t, e) -> {
      String disturb = JSONUtils.toJson(t.getDisturb());
      e.setDisturb(disturb);
      String content = JSONUtils.toJson(contentList);
      e.setContent(content);

      //设置默认参数
      e.setStatus(PostRadiotelephoneTrainStatusEnum.NOT_STARTED.getStatus());
      e.setUserId(userEntity.getId());
      e.setDuration(0);
      e.setAccuracy(new BigDecimal("0"));
      e.setErrorNumber(0);
      e.setPassNumber(0);
      e.setCreateTime(LocalDateTime.now());
    });
    PostRadiotelephoneTrainEntity save = dao.save(entity);
    return assembleData(save, false);
  }

  public List<PostRadiotelephoneVO> listPage(String token) throws Exception {
    UserEntity userEntity = userDao.findUserEntityByToken(token);
    List<PostRadiotelephoneTrainEntity> entityList = dao.findByUserIdOrderByCreateTimeDesc(userEntity.getId());
    List<PostRadiotelephoneVO> list = new ArrayList<>(entityList.size());
    for (PostRadiotelephoneTrainEntity e : entityList) {
      list.add(assembleData(e, true));
    }

    return list;
  }

  @Transactional
  public void begin(PostRadiotelephoneVO vo) {
    PostRadiotelephoneTrainEntity entity = dao.findById(vo.getId());
    entity.setStatus(PostRadiotelephoneTrainStatusEnum.UNDERWAY.getStatus());
    entity.setStartTime(LocalDateTime.now());
  }

  @Transactional
  public void finish(PostRadiotelephoneVO vo) {
    PostRadiotelephoneTrainEntity entity = dao.findById(vo.getId());
    entity.setEndTime(LocalDateTime.now());
    //状态设置成完成
    entity.setStatus(PostRadiotelephoneTrainStatusEnum.FINISH.getStatus());
    long start = entity.getStartTime().toEpochSecond(ZoneOffset.of("+8"));
    long end = entity.getEndTime().toEpochSecond(ZoneOffset.of("+8"));
    entity.setDuration((int) (end - start));
    entity.setContent(vo.getContent());
    entity.setPassNumber(vo.getPassNumber());
    entity.setErrorNumber(vo.getErrorNumber());
    entity.setAccuracy(vo.getAccuracy());
    entity.setScore(vo.getScore());
  }

  public PostRadiotelephoneVO details(PostRadiotelephoneVO vo) {
    return assembleData(dao.findById(vo.getId()), false);
  }

  public Boolean delete(String id) {
    return dao.deleteById(id);
  }

  private PostRadiotelephoneVO assembleData(PostRadiotelephoneTrainEntity entity, boolean isList) {
    List<String> disturb = StringUtils.isEmpty(entity.getDisturb())
        ? new ArrayList<>()
        : JSONUtils.fromJson(entity.getDisturb(), new TypeToken<>() {
    });
    return new PostRadiotelephoneVO(entity.getId(), entity.getName(), entity.getStatus(), entity.getType(),
        entity.getSpeed(), entity.getNumber(),
        disturb.stream().map(Integer::parseInt).toList(), entity.getDuration(),
        isList ? null : entity.getContent(), entity.getAccuracy(), entity.getErrorNumber(),
        entity.getPassNumber(), entity.getScore(), entity.getTrainType()
    );
  }
}
