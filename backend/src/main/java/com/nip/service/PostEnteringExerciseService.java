package com.nip.service;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.JSONUtils;
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
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
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
  public void begin(PostEnteringExerciseUpdateParam param, String token) {
    PostEnteringExerciseEntity entity = ownedTrain(param.getId(), token, true);
    if (Objects.equals(entity.getStatus(), PostEnteringExerciseStatusEnum.FINISH.getStatus())) {
      throw new TerminalStateException("训练已结束");
    }
    if (Objects.equals(entity.getStatus(), PostEnteringExerciseStatusEnum.UNDERWAY.getStatus())) {
      return;
    }
    if (!Objects.equals(entity.getStatus(), PostEnteringExerciseStatusEnum.NOT_STARTED.getStatus())) {
      throw new IllegalArgumentException("训练状态无效");
    }
    entity.setStatus(PostEnteringExerciseStatusEnum.UNDERWAY.getStatus());
    entity.setStartTime(LocalDateTime.now());
  }

  @Transactional(rollbackOn = Exception.class)
  public void finish(PostEnteringExerciseFinishParam param, String token) {
    PostEnteringExerciseEntity entity = ownedTrain(param.getId(), token, true);
    if (Objects.equals(entity.getStatus(), PostEnteringExerciseStatusEnum.FINISH.getStatus())) {
      if (sameAnswers(entity.getContent(), param.getContent())) {
        return;
      }
      throw new TerminalStateException("训练已结束，不能修改结果");
    }
    if (!Objects.equals(entity.getStatus(), PostEnteringExerciseStatusEnum.UNDERWAY.getStatus())
        || entity.getStartTime() == null) {
      throw new IllegalArgumentException("训练尚未开始");
    }
    if (param.getAccuracy() != null || param.getSpeed() != null || param.getDuration() != null
        || param.getCorrectNum() != null || param.getErrorNum() != null) {
      throw new IllegalArgumentException("成绩和用时由服务端计算，请仅提交录入内容");
    }
    JsonArray source = contentArray(entity.getContent());
    JsonArray answers = contentArray(param.getContent());
    Integer type = entity.getType();
    if (type == null || type < 0 || type > 4 || source.isEmpty() || answers.isEmpty()) {
      throw new IllegalArgumentException("训练题面或录入内容无效");
    }
    boolean terms = type == 1 || type == 3;
    boolean english = Objects.equals(type, PostEnteringExerciseTypeEnum.LYCZ.getCode());
    StringBuilder expected = new StringBuilder();
    for (JsonElement item : source) {
      String text = text(item);
      expected.append(terms ? text : english ? englishDisplay(text.trim()) : text.trim());
    }
    StringBuilder submitted = new StringBuilder();
    StringBuilder input = new StringBuilder();
    int correct = 0;
    int entered = 0;
    int total = 0;
    if (terms && source.size() != answers.size()) {
      throw new IllegalArgumentException("录入条目与训练题面不一致");
    }
    for (int i = 0; i < answers.size(); i++) {
      if (!answers.get(i).isJsonObject()) {
        throw new IllegalArgumentException("录入条目无效");
      }
      JsonObject row = answers.get(i).getAsJsonObject();
      String font = text(row.get("font"));
      String value = text(row.get("value"));
      entered += Math.min(value.length(), font.length());
      JsonObject captured = new JsonObject();
      captured.addProperty("font", font);
      captured.addProperty("value", value);
      captured.addProperty("isFirst", row.has("isFirst") && row.get("isFirst").isJsonPrimitive()
          && row.get("isFirst").getAsJsonPrimitive().isBoolean() && row.get("isFirst").getAsBoolean());
      captured.addProperty("trueOrfalse", font.equals(value));
      answers.set(i, captured);
      submitted.append(font);
      input.append(value);
      if (terms) {
        if (!font.equals(text(source.get(i)))) {
          throw new IllegalArgumentException("录入条目与训练题面不一致");
        }
        correct += font.equals(value) ? 1 : 0;
        total++;
      }
    }
    if (expected.isEmpty() || !expected.toString().contentEquals(submitted)) {
      throw new IllegalArgumentException("录入题面与保存的训练内容不一致");
    }
    if (english) {
      String[] target = expected.toString().split(" ", -1);
      String[] actual = input.toString().split(" ", -1);
      total = Math.max(target.length, actual.length);
      entered = Math.min(target.length, (int) java.util.Arrays.stream(actual).filter(word -> !word.isEmpty()).count());
      for (int i = 0; i < target.length; i++) {
        correct += i < actual.length && target[i].equals(actual[i]) ? 1 : 0;
      }
    } else if (!terms) {
      total = Math.max(expected.length(), input.length());
      for (int i = 0; i < expected.length(); i++) {
        correct += i < input.length() && expected.charAt(i) == input.charAt(i) ? 1 : 0;
      }
    }
    LocalDateTime end = LocalDateTime.now();
    long seconds = Duration.between(entity.getStartTime(), end).getSeconds();
    if (seconds < 0 || seconds > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("训练开始时间无效");
    }
    entity.setAccuracy(BigDecimal.valueOf(correct * 100L)
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue());
    entity.setSpeed((int) Math.min(Integer.MAX_VALUE, entered * 60L / Math.max(1, seconds)));
    entity.setDuration((int) seconds);
    entity.setCorrectNum(correct);
    entity.setErrorNum(total - correct);
    entity.setContent(JSONUtils.toJson(answers));
    entity.setEndTime(end);
    entity.setStatus(PostEnteringExerciseStatusEnum.FINISH.getStatus());
  }

  private boolean sameAnswers(String stored, String submitted) {
    if (submitted == null) {
      return false;
    }
    JsonArray previous = contentArray(stored);
    JsonArray current = contentArray(submitted);
    if (previous.size() != current.size()) {
      return false;
    }
    for (int i = 0; i < previous.size(); i++) {
      if (!previous.get(i).isJsonObject() || !current.get(i).isJsonObject()) {
        return false;
      }
      JsonObject left = previous.get(i).getAsJsonObject();
      JsonObject right = current.get(i).getAsJsonObject();
      if (!Objects.equals(left.get("font"), right.get("font"))
          || !Objects.equals(left.get("value"), right.get("value"))) {
        return false;
      }
    }
    return true;
  }

  // English practice inserts a space before punctuation for its word-level display.
  private String englishDisplay(String source) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < source.length(); i++) {
      char value = source.charAt(i);
      if ("!.,?;:'{}[]()@#$%^&*".indexOf(value) >= 0) {
        result.append(' ');
      }
      result.append(value);
    }
    return result.toString();
  }

  private JsonArray contentArray(String content) {
    try {
      JsonArray array = JSONUtils.fromJson(content, JsonArray.class);
      if (array == null) {
        throw new IllegalArgumentException("训练内容不能为空");
      }
      return array;
    } catch (JsonParseException | IllegalStateException exception) {
      throw new IllegalArgumentException("训练内容必须为有效的数组", exception);
    }
  }

  private String text(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("训练内容必须包含有效文本");
    }
    return value.getAsString();
  }

  public PostEnteringExerciseVO getById(String id, String token) {
    PostEnteringExerciseEntity entity = ownedTrain(id, token, false);
    PostEnteringExerciseVO view = PojoUtils.convertOne(entity, PostEnteringExerciseVO.class);
    if (Objects.equals(entity.getStatus(), PostEnteringExerciseStatusEnum.UNDERWAY.getStatus()) && entity.getStartTime() != null) {
      view.setDuration(Math.toIntExact(Duration.between(entity.getStartTime(), LocalDateTime.now()).getSeconds()));
    }
    return view;
  }

  private PostEnteringExerciseEntity ownedTrain(String id, String token, boolean lock) {
    String actor = userService.getUserByToken(token).getId();
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    PostEnteringExerciseEntity entity = (lock
        ? exerciseDao.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
        : exerciseDao.findByIdOptional(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(actor, entity.getCreateUserId(), "个人录入练习 " + id);
    return entity;
  }

  /**
   * 删除个人录入练习。属主字段是 {@code createUserId}（各域字段名不同，这里显式传入）。
   */
  @Transactional
  public boolean delete(String id, String token) {
    ownedTrain(id, token, true);
    return exerciseDao.deleteById(id);
  }

}
