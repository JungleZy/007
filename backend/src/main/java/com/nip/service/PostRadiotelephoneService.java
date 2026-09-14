package com.nip.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.nip.common.exception.TerminalStateException;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostRadiotelephoneTrainStatusEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostRadiotelephoneDao;
import com.nip.dao.RadiotelephoneTermDataDao;
import com.nip.dto.PostRadiotelephoneDto;
import com.nip.dto.vo.PostRadiotelephoneVO;
import com.nip.entity.PostRadiotelephoneTermDataEntity;
import com.nip.entity.PostRadiotelephoneTrainEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-22 17:44
 * @Description:
 */
@ApplicationScoped
public class PostRadiotelephoneService {

  private final PostRadiotelephoneDao dao;
  private final RadiotelephoneTermDataDao dataDao;
  private final UserService userService;

  /** 属主判定的唯一口径（个人域 = 仅创建者）。 */
  @Inject TrainWriteAccess trainWriteAccess;

  @Inject
  public PostRadiotelephoneService(PostRadiotelephoneDao dao, RadiotelephoneTermDataDao dataDao, UserService userService) {
    this.dao = dao;
    this.dataDao = dataDao;
    this.userService = userService;
  }

  @Transactional
  public PostRadiotelephoneVO add(String token, PostRadiotelephoneDto dto) {
    // DATA-03：走 userService.getUserByToken，token 失效时抛 UnauthorizedException（200+code203），不再裸解引用 NPE
    UserEntity userEntity = userService.getUserByToken(token);
    List<PostRadiotelephoneTermDataEntity> entityList = dataDao.findByTypeOrderByKey(dto.getType());
    // P2-20：单点取值无 +1/成对语义，size-1 会漏掉末条且 size<=1 时抛异常
    if (entityList.isEmpty()) {
      throw new IllegalArgumentException("该类型下无词条，无法生成训练内容");
    }
    if (dto.getNumber() == null) {
      throw new IllegalArgumentException("词条数量不能为空");
    }
    List<PostRadiotelephoneTermDataEntity> contentList = new ArrayList<>();
    for (int i = 0; i < dto.getNumber(); i++) {
      int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(entityList.size());
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
    UserEntity userEntity = userService.getUserByToken(token);
    List<PostRadiotelephoneTrainEntity> entityList = dao.findByUserIdOrderByCreateTimeDesc(userEntity.getId());
    List<PostRadiotelephoneVO> list = new ArrayList<>(entityList.size());
    for (PostRadiotelephoneTrainEntity e : entityList) {
      list.add(assembleData(e, true));
    }

    return list;
  }

  @Transactional
  public void begin(PostRadiotelephoneVO vo, String token) {
    PostRadiotelephoneTrainEntity entity = ownedTrain(vo.getId(), token, true);
    if (Objects.equals(entity.getStatus(), PostRadiotelephoneTrainStatusEnum.FINISH.getStatus())) {
      throw new TerminalStateException("训练已结束");
    }
    if (Objects.equals(entity.getStatus(), PostRadiotelephoneTrainStatusEnum.UNDERWAY.getStatus())) {
      return;
    }
    if (!Objects.equals(entity.getStatus(), PostRadiotelephoneTrainStatusEnum.NOT_STARTED.getStatus())) {
      throw new IllegalArgumentException("训练状态无效");
    }
    entity.setStatus(PostRadiotelephoneTrainStatusEnum.UNDERWAY.getStatus());
    entity.setStartTime(LocalDateTime.now());
  }

  @Transactional
  public void finish(PostRadiotelephoneVO vo, String token) {
    PostRadiotelephoneTrainEntity entity = ownedTrain(vo.getId(), token, true);
    if (Objects.equals(entity.getStatus(), PostRadiotelephoneTrainStatusEnum.FINISH.getStatus())) {
      if (sameAnswers(entity.getContent(), vo.getContent())) {
        return;
      }
      throw new TerminalStateException("训练已结束，不能修改结果");
    }
    if (!Objects.equals(entity.getStatus(), PostRadiotelephoneTrainStatusEnum.UNDERWAY.getStatus())
        || entity.getStartTime() == null) {
      throw new IllegalArgumentException("训练尚未开始");
    }
    if (vo.getScore() != null || vo.getAccuracy() != null || vo.getPassNumber() != null
        || vo.getErrorNumber() != null || vo.getDuration() != null) {
      throw new IllegalArgumentException("成绩和用时由服务端计算，请仅提交作答内容");
    }
    JsonArray source = contentArray(entity.getContent());
    JsonArray answers = contentArray(vo.getContent());
    if (source.isEmpty() || source.size() != answers.size()
        || !Objects.equals(entity.getNumber(), source.size())
        || entity.getTrainType() == null || entity.getTrainType() < 0 || entity.getTrainType() > 1
        || entity.getType() == null || entity.getType() < 0 || entity.getType() > 1) {
      throw new IllegalArgumentException("作答条目与训练题面不一致");
    }
    int correct = 0;
    for (int i = 0; i < source.size(); i++) {
      JsonObject target = object(source.get(i));
      JsonObject submitted = object(answers.get(i));
      String key = text(target.get("key"));
      String value = text(target.get("value"));
      if (!key.equals(text(submitted.get("key"))) || !value.equals(text(submitted.get("value")))) {
        throw new IllegalArgumentException("作答题面与保存的训练内容不一致");
      }
      JsonObject answer = object(submitted.get("answer"));
      String answerKey = text(answer.get("key"));
      String answerValue = text(answer.get("value"));
      boolean passed = entity.getTrainType() == 0
          ? key.equals(answerKey.trim()) && value.trim().equals(answerValue)
          : entity.getType() == 0 ? key.trim().equals(answerKey.trim()) : value.trim().equals(answerValue.trim());
      correct += passed ? 1 : 0;
      JsonObject captured = new JsonObject();
      captured.addProperty("key", answerKey);
      captured.addProperty("value", answerValue);
      target.add("answer", captured);
      target.addProperty("trueOrfalse", passed);
    }
    LocalDateTime end = LocalDateTime.now();
    long seconds = Duration.between(entity.getStartTime(), end).getSeconds();
    if (seconds < 0 || seconds > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("训练开始时间无效");
    }
    entity.setDuration((int) seconds);
    entity.setContent(JSONUtils.toJson(source));
    entity.setPassNumber(correct);
    entity.setErrorNumber(source.size() - correct);
    entity.setAccuracy(BigDecimal.valueOf(correct * 100L)
        .divide(BigDecimal.valueOf(source.size()), 2, RoundingMode.HALF_UP));
    entity.setScore(BigDecimal.valueOf(correct * 100L)
        .divide(BigDecimal.valueOf(source.size()), 1, RoundingMode.HALF_UP));
    entity.setEndTime(end);
    entity.setStatus(PostRadiotelephoneTrainStatusEnum.FINISH.getStatus());
  }

  private boolean sameAnswers(String stored, String submitted) {
    JsonArray previous = contentArray(stored);
    JsonArray current = contentArray(submitted);
    if (previous.size() != current.size()) {
      return false;
    }
    for (int i = 0; i < previous.size(); i++) {
      JsonObject left = object(previous.get(i));
      JsonObject right = object(current.get(i));
      if (!Objects.equals(left.get("key"), right.get("key"))
          || !Objects.equals(left.get("value"), right.get("value"))
          || !Objects.equals(left.get("answer"), right.get("answer"))) {
        return false;
      }
    }
    return true;
  }

  private JsonArray contentArray(String content) {
    try {
      JsonArray array = JSONUtils.fromJson(content, JsonArray.class);
      if (array == null) {
        throw new IllegalArgumentException("作答内容不能为空");
      }
      return array;
    } catch (JsonParseException | IllegalStateException exception) {
      throw new IllegalArgumentException("作答内容必须为有效的数组", exception);
    }
  }

  private JsonObject object(JsonElement value) {
    if (value == null || !value.isJsonObject()) {
      throw new IllegalArgumentException("作答条目无效");
    }
    return value.getAsJsonObject();
  }

  private String text(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("作答条目必须包含有效文本");
    }
    return value.getAsString();
  }

  public PostRadiotelephoneVO details(PostRadiotelephoneVO vo, String token) {
    return assembleData(ownedTrain(vo.getId(), token, false), false);
  }

  private PostRadiotelephoneTrainEntity ownedTrain(String id, String token, boolean lock) {
    String actor = userService.getUserByToken(token).getId();
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    PostRadiotelephoneTrainEntity entity = (lock ? dao.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
        : dao.findByIdOptional(id)).orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(actor, entity.getUserId(), "个人无线电话训练 " + id);
    return entity;
  }

  /**
   * 删除个人无线电话训练。属主字段是 {@code userId}（各域字段名不同，这里显式传入）。
   */
  @Transactional
  public Boolean delete(String id, String token) {
    ownedTrain(id, token, true);
    return dao.deleteById(id);
  }

  private PostRadiotelephoneVO assembleData(PostRadiotelephoneTrainEntity entity, boolean isList) {
    List<String> disturb = StringUtils.isEmpty(entity.getDisturb())
        ? new ArrayList<>()
        : JSONUtils.fromJson(entity.getDisturb(), new TypeToken<>() {
    });
    Integer duration = entity.getDuration();
    if (Objects.equals(entity.getStatus(), PostRadiotelephoneTrainStatusEnum.UNDERWAY.getStatus()) && entity.getStartTime() != null) {
      duration = Math.toIntExact(Duration.between(entity.getStartTime(), LocalDateTime.now()).getSeconds());
    }
    return new PostRadiotelephoneVO(entity.getId(), entity.getName(), entity.getStatus(), entity.getType(),
        entity.getSpeed(), entity.getNumber(),
        disturb.stream().map(Integer::parseInt).toList(), duration,
        isList ? null : entity.getContent(), entity.getAccuracy(), entity.getErrorNumber(),
        entity.getPassNumber(), entity.getScore(), entity.getTrainType()
    );
  }
}
