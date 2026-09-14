package com.nip.service;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostMilitaryTermTrainStatusEnum;
import com.nip.common.exception.NIPException;
import com.nip.common.exception.UnauthorizedException;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.dao.PostMilitaryTermTrainDao;
import com.nip.dao.PostMilitaryTermTrainTestPaperDao;
import com.nip.dto.PostMilitaryTermTrainAddDto;
import com.nip.dto.PostMilitaryTermTrainFinishDto;
import com.nip.dto.vo.PostMilitaryTermTrainVO;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.entity.PostMilitaryTermTrainEntity;
import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author: wushilin
 * @Data: 2022-06-24 15:33
 * @Description:
 */
@Slf4j
@ApplicationScoped
public class PostMilitaryTermTrainService {

  private final PostMilitaryTermTrainDao termTrainDao;
  private final MilitaryTermDataDao dataDao;
  private final PostMilitaryTermTrainTestPaperDao testPaperDao;
  private final UserService userService;

  /** 属主判定的唯一口径（个人域 = 仅创建者）。 */
  @Inject TrainWriteAccess trainWriteAccess;

  @Inject
  public PostMilitaryTermTrainService(PostMilitaryTermTrainDao termTrainDao, MilitaryTermDataDao dataDao,
                                      PostMilitaryTermTrainTestPaperDao testPaperDao, UserService userService) {
    this.termTrainDao = termTrainDao;
    this.dataDao = dataDao;
    this.testPaperDao = testPaperDao;
    this.userService = userService;
  }

  @Transactional
  public PostMilitaryTermTrainVO add(PostMilitaryTermTrainAddDto dto, String token) {
    try {
      // DATA-03：走 userService.getUserByToken，token 失效时抛 UnauthorizedException（200+code203），不再裸解引用 NPE
      UserEntity userEntity = userService.getUserByToken(token);

      PostMilitaryTermTrainEntity trainEntity = PojoUtils.convertOne(dto, PostMilitaryTermTrainEntity.class, (d, e) -> {
        e.setAccuracy(new BigDecimal(0));
        e.setCorrectNumber(0);
        e.setDuration(0);
        e.setErrorNumber(0);
        e.setTypes(JSONUtils.toJson(dto.getTypes()));
        e.setStatus(PostMilitaryTermTrainStatusEnum.NOT_STARTED.getStatus());
        e.setUserId(userEntity.getId());
        e.setScore(new BigDecimal("0"));
        e.setCreateTime(LocalDateTime.now());
      });

      if (dto.getTypes().isEmpty()) {
        List<String> collect = dataDao.findAllByParentId("0").stream().map(MilitaryTermDataEntity::getId)
            .toList();
        dto.setTypes(collect);
      }

      //保存训练
      PostMilitaryTermTrainEntity save = termTrainDao.save(trainEntity);
      Map<String, List<MilitaryTermDataEntity>> dataMap = dataDao.findAllByParentIdIn(dto.getTypes()).stream().collect(
          Collectors.groupingBy(MilitaryTermDataEntity::getParentId));
      //保存考题数据
      List<PostMilitaryTermTrainTestPaperEntity> testPaperEntityList = new ArrayList<>();
      Iterator<String> iterator = dataMap.keySet().iterator();
      List<String> types = new ArrayList<>();
      while (iterator.hasNext()) {
        String key = iterator.next();
        if (dataMap.get(key).size() >= 4) {
          types.add(key);
        }
      }
      dto.setTypes(types);
      if (ObjectUtil.isEmpty(dataMap)) {
        throw new NIPException("该类型条目少于4条，不能生成训练，请重新选择类型");
      }
      generateTestPaper(dto, save, dataMap, testPaperEntityList);
      //将考试题目保存到数据库中
      PojoUtils.averageAssign(testPaperEntityList, 100).forEach(testPaperDao::save);
      return PojoUtils.convertOne(save, PostMilitaryTermTrainVO.class, (e, v) -> {
        List<MilitaryTermDataEntity> militaryTermDataEntities = dataDao.findAllByIdIn(dto.getTypes());
        List<String> names = militaryTermDataEntities.stream().map(MilitaryTermDataEntity::getKey)
            .toList();
        v.setTypes(names);
      });
    } catch (IllegalArgumentException | IllegalStateException | UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      log.error("创建军事术语训练失败", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 生成考题
   *
   * @param: dto
   * @param: save
   * @param: dataMap
   * @param: testPaperEntityList
   */
  public void generateTestPaper(PostMilitaryTermTrainAddDto dto, PostMilitaryTermTrainEntity save,
                                Map<String, List<MilitaryTermDataEntity>> dataMap,
                                List<PostMilitaryTermTrainTestPaperEntity> testPaperEntityList) {
    //生成训练考题
    for (int i = 0; i < dto.getTotalNumber(); i++) {
      int dataIndex;
      if (dto.getTypes().size() == 1) {
        dataIndex = 0;
      } else {
        dataIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(dto.getTypes().size());
      }
      String dataId = dto.getTypes().get(dataIndex);
      //获得该类型的所有考题
      List<MilitaryTermDataEntity> militaryTermDataEntities = dataMap.get(dataId);
      if (militaryTermDataEntities == null) {
        i--;
        continue;
      }
      long distinct = militaryTermDataEntities.stream()
          .map(MilitaryTermDataEntity::getValue)
          .filter(ObjectUtil::isNotEmpty)
          .distinct()
          .count();
      if (distinct < 4) {
        throw new IllegalArgumentException("类型 " + dataId + " 有效题目不足4条，无法生成干扰项");
      }
      //考试题目
      int titleIndex;
      if (militaryTermDataEntities.size() == 1) {
        titleIndex = 0;
      } else {
        titleIndex = java.util.concurrent.ThreadLocalRandom.current().nextInt(militaryTermDataEntities.size());
      }
      //正确答案
      MilitaryTermDataEntity dataEntity = militaryTermDataEntities.get(titleIndex);
      //存放选项
      List<String> options = new ArrayList<>();
      //放入正确答案
      options.add(dataEntity.getValue());
      //循环3次拿同类型的其它错误答案
      int flag = 1;

      //判断是否生成与正确答案类型的选项
      boolean keyword = checkKeyword(dataEntity.getValue(), options);
      if (keyword) {
        flag = 2;
      }
      //封装test_paper对象
      PostMilitaryTermTrainTestPaperEntity testPaperEntity = new PostMilitaryTermTrainTestPaperEntity();
      int attempts = 0;
      while (flag <= 3) {
        if (++attempts > 100) {
          log.warn("干扰项随机生成超过100次未完成，降级为顺序补足，title={}", dataEntity.getKey());
          for (MilitaryTermDataEntity entity : militaryTermDataEntities) {
            String v = entity.getValue();
            if (ObjectUtil.isNotEmpty(v) && !options.contains(v)) {
              options.add(v);
              if (options.size() >= 4) {
                break;
              }
            }
          }
          break;
        }
        int optionId;
        if (militaryTermDataEntities.size() == 1) {
          optionId = 0;
        } else {
          //随机其它选项
          optionId = java.util.concurrent.ThreadLocalRandom.current().nextInt(militaryTermDataEntities.size());
        }
        if (titleIndex != optionId || optionId == 0) {
          MilitaryTermDataEntity entity = militaryTermDataEntities.get(optionId);
          if (ObjectUtil.isNotEmpty(entity.getValue())) {
            String value = entity.getValue();
            if (options.stream().anyMatch(s -> s.equals(value))) {
              //放入options
              boolean b = checkKeyword(value, options);
              if (b) {
                flag++;
              }
            } else {
              options.add(value);
              flag++;
            }
          }
        }
      }
      int keyNum = 65;
      Map<String, String> optionMap = new LinkedHashMap<>();
      //对选项进行排序
      options.sort(Comparator.comparingInt(String::hashCode));
      //排序完成后，添加到optionMap中
      for (String option : options) {
        // A B C D
        String word = String.valueOf((char) keyNum);
        optionMap.put(word, option);
        //找到正确答案,存入对象中
        if (Objects.equals(option, dataEntity.getValue())) {
          testPaperEntity.setCorrectAnswer(word);
        }
        keyNum++;
      }
      testPaperEntity.setOption(JSONUtils.toJson(optionMap));
      testPaperEntity.setTitle(dataEntity.getKey());
      testPaperEntity.setTrainId(save.getId());
      testPaperEntityList.add(testPaperEntity);
    }
  }

  /**
   * 判断是否生成与正确答案相同的选项
   *
   * @param: answer 答案
   * @param: options
   */
  public boolean checkKeyword(String answer, List<String> options) {
    boolean flag = false;
    //匹配数字区间 例如：执行喷火任务的小组。由喷火分队派出。通常由喷火手和单兵火箭手4～6人混合编成,装备有喷火器及防化单兵火箭2～3具。可单独配属给步兵分队遂行战斗任务,也可在班的编成内行动。
    if (Pattern.matches("^.*\\d{1,}.{0,3}～\\d{1,}.*", answer)) {
      StringBuilder firstBuilder = new StringBuilder();
      StringBuilder lastBuilder = new StringBuilder();
      String[] split = answer.split("～");
      char[] chars = split[0].trim().toCharArray();
      char[] chars1 = split[1].trim().toCharArray();
      //～ 前面的数字
      for (int j = chars.length - 1; j >= 0; j--) {
        char aChar = chars[j];
        if (aChar >= 48 && aChar <= 57) {
          firstBuilder.append(aChar);
          continue;
        }
        if (j < (chars.length - 1) - 3) {
          break;
        }
      }
      //～ 后面的数字
      for (char aChar : chars1) {
        if (aChar >= 48 && aChar <= 57) {
          lastBuilder.append(aChar);
          continue;
        }
        break;
      }

      //前后都+1个单位
      String firstStr = firstBuilder.reverse().toString();
      String lastStr = lastBuilder.toString();
      StringBuilder lastAdd = new StringBuilder("1");
      String firstAdd = "1" + "0".repeat(Math.max(0, firstStr.length() - 1));
      lastAdd.append("0".repeat(Math.max(0, lastStr.length() - 1)));

      if (StringUtils.isEmpty(firstStr) || StringUtils.isEmpty(lastStr)) {
        //未解析出
        log.error("范围解析异常：{}", answer);
      }

      //开始相加
      String newFirstStr = String.valueOf(Integer.parseInt(firstAdd) + Integer.parseInt(firstStr));
      String newLastStr = String.valueOf(Integer.parseInt(lastAdd.toString()) + Integer.parseInt(lastStr));

      //把该选项添加到这里面
      String e = answer.replaceFirst(lastStr, newLastStr)
          .replaceFirst(firstStr, newFirstStr);
      if (options == null) {
        options = new ArrayList<>(); // 或者根据需求初始化为其他类型
      }

      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }
    if (options == null) {
      options = new ArrayList<>(); // 或者根据需求初始化为其他类型
    }
    //无线->有线
    if (!flag && answer.contains("无线")) {
      String e = answer.replaceFirst("无线", "有线");

      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //出口变入口 入口变出口
    if (!flag && (answer.contains("出口") || answer.contains("入口"))) {
      String e = answer.contains("出口") ? answer.replaceFirst("出口", "入口") : answer.replaceFirst("入口", "出口");
      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //干线替换成主线
    if (!flag && answer.contains("干线")) {
      String e = answer.replaceFirst("干线", "主线");
      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //小时替换成分钟
    if (!flag && answer.contains("小时")) {
      String e = answer.replaceAll("小时", "分钟");
      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //线状天线改面状天线 反之 面状天线改线状天线
    if (!flag && (answer.contains("线状") || answer.contains("面状"))) {
      String e = answer.contains("线状") ? answer.replaceFirst("线状", "面状") : answer.replaceFirst("面状", "线状");
      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //接收->发射 或 发射->接收啊
    if (!flag && (answer.contains("接收") || answer.contains("发射"))) {
      String e = answer.contains("接收") ? answer.replaceFirst("接收", "发射") : answer.replaceFirst("发射", "接收");
      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //战术->战役 或 战役->战术
    if (!flag && (answer.contains("战术") || answer.contains("战役"))) {
      String e = answer.contains("战术") ? answer.replaceFirst("战术", "战役") : answer.replaceFirst("战役", "战术");
      Set<String> optionSet = new HashSet<>(options);
      if (!optionSet.contains(e)) {
        options.add(e);
        flag = true;
      }
    }

    //如出现3个以上 "、" 删除中间一个
    if (!flag && Pattern.matches("^.*、.{1,}、.{1,}$.*", answer)) {
      String[] split = answer.split("、");
      if (split.length > 3) {
        List<String> collect = new ArrayList<>(Arrays.stream(split).toList());
        collect.remove(1);
        String e = String.join("、", collect);
        Set<String> optionSet = new HashSet<>(options);
        if (!optionSet.contains(e)) {
          options.add(e);
          flag = true;
        }
      }
    }

    //对数字进行修改
    if (!flag && Pattern.matches("^.*\\d+.*", answer)) {
      String number = extractFirstNumber(answer);
      if (number != null) {
        Integer rNumber = java.util.concurrent.ThreadLocalRandom.current().nextInt(9) + 1;
        while (rNumber.equals(Integer.valueOf(number))) {
          rNumber = java.util.concurrent.ThreadLocalRandom.current().nextInt(9) + 1;
        }
        String e = answer.replaceFirst(number, rNumber.toString());
        Set<String> optionSet = new HashSet<>(options);
        if (!optionSet.contains(e)) {
          options.add(e);
          flag = true;
        }
      }
    }
    return flag;
  }

  private String extractFirstNumber(String str) {
    Matcher matcher = Pattern.compile("\\d+").matcher(str);
    if (matcher.find()) {
      return matcher.group();
    }
    return null;
  }

  public List<PostMilitaryTermTrainVO> listPage(String token) {
    // RevPh4 P3：走 userService.getUserByToken，登录失效统一抛 UnauthorizedException（203 语义）
    UserEntity userEntity = userService.getUserByToken(token);
    List<PostMilitaryTermTrainEntity> ret = termTrainDao.findByUserIdOrderByCreateTimeDesc(userEntity.getId());
    return PojoUtils.convert(ret, PostMilitaryTermTrainVO.class, (e, v) -> v.setTypes(
        dataDao.findAllByIdIn(JSONUtils.fromJson(e.getTypes(), new TypeToken<>() {
            }))
            .stream()
            .map(MilitaryTermDataEntity::getKey)
            .toList()));
  }

  public PostMilitaryTermTrainVO details(PostMilitaryTermTrainVO vo, String token) {
    PostMilitaryTermTrainEntity entity = ownedTrain(vo.getId(), token, false);
    boolean completed = Objects.equals(entity.getStatus(), PostMilitaryTermTrainStatusEnum.FINISH.getStatus());
    List<PostMilitaryTermTrainTestPaperEntity> papers = testPaperDao.findAllByTrainId(entity.getId()).stream()
        .map(paper -> paperForView(paper, completed))
        .toList();
    return PojoUtils.convertOne(entity, PostMilitaryTermTrainVO.class, (source, view) -> {
      view.setTestPaperList(papers);
      List<String> ids = JSONUtils.fromJson(source.getTypes(), new TypeToken<>() {});
      view.setTypes(dataDao.findAllByIdIn(ids).stream().map(MilitaryTermDataEntity::getKey).toList());
    });
  }

  @Transactional(rollbackOn = Exception.class)
  public PostMilitaryTermTrainVO begin(String id, String token) {
    PostMilitaryTermTrainEntity entity = ownedTrain(id, token, true);
    if (Objects.equals(entity.getStatus(), PostMilitaryTermTrainStatusEnum.FINISH.getStatus())) {
      throw new TerminalStateException("训练已完成，不能重新开始");
    }
    if (Objects.equals(entity.getStatus(), PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus())) {
      return PojoUtils.convertOne(entity, PostMilitaryTermTrainVO.class);
    }
    if (!Objects.equals(entity.getStatus(), PostMilitaryTermTrainStatusEnum.NOT_STARTED.getStatus())) {
      throw new IllegalArgumentException("训练状态无效");
    }
    entity.setStatus(PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus());
    entity.setStartTime(LocalDateTime.now());
    return PojoUtils.convertOne(entity, PostMilitaryTermTrainVO.class);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostMilitaryTermTrainVO finish(PostMilitaryTermTrainFinishDto dto, String token) {
    if (dto == null) {
      throw new IllegalArgumentException("训练参数不能为空");
    }
    PostMilitaryTermTrainEntity train = ownedTrain(dto.getId(), token, true);
    // 训练锁等待后，题目也用当前读，确保并发交卷能看到已提交答案。
    List<PostMilitaryTermTrainTestPaperEntity> papers = testPaperDao.find("trainId", train.getId())
        .withLock(LockModeType.PESSIMISTIC_WRITE)
        .list();
    Map<String, PostMilitaryTermTrainTestPaperEntity> paperById = papers.stream()
        .collect(Collectors.toMap(PostMilitaryTermTrainTestPaperEntity::getId, paper -> paper));
    List<PostMilitaryTermTrainTestPaperEntity> submitted = Optional.ofNullable(dto.getTestPaperList()).orElse(List.of());
    Map<String, Map<String, String>> optionsByPaperId = validateSubmission(submitted, paperById);

    if (Objects.equals(train.getStatus(), PostMilitaryTermTrainStatusEnum.FINISH.getStatus())) {
      if (sameAnswers(submitted, paperById)) {
        return PojoUtils.convertOne(train, PostMilitaryTermTrainVO.class);
      }
      throw new TerminalStateException("训练已完成，不能修改结果");
    }
    if (Objects.equals(train.getStatus(), PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus())
        && train.getStartTime() == null) {
      throw new TerminalStateException("训练开始时间缺失，不能完成");
    }
    if (!Objects.equals(train.getStatus(), PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus())) {
      throw new IllegalArgumentException("训练尚未开始");
    }

    int correct = 0;
    int answered = 0;
    for (PostMilitaryTermTrainTestPaperEntity answer : submitted) {
      PostMilitaryTermTrainTestPaperEntity paper = paperById.get(answer.getId());
      paper.setUserAnswer(answer.getUserAnswer());
      if (answer.getUserAnswer() == null) {
        continue;
      }
      answered++;
      Map<String, String> options = optionsByPaperId.get(answer.getId());
      String userValue = options.get(answer.getUserAnswer());
      String correctValue = options.get(paper.getCorrectAnswer());
      if (Objects.equals(userValue, correctValue)) {
        correct++;
      }
    }

    train.setStatus(PostMilitaryTermTrainStatusEnum.FINISH.getStatus());
    train.setEndTime(LocalDateTime.now());
    train.setCorrectNumber(correct);
    train.setErrorNumber(answered - correct);
    train.setAccuracy(answered == 0
        ? BigDecimal.ZERO
        : new BigDecimal(correct).divide(new BigDecimal(answered), 2, RoundingMode.HALF_UP)
            .multiply(new BigDecimal(100)));
    train.setScore(papers.isEmpty()
        ? BigDecimal.ZERO
        : new BigDecimal(correct).divide(new BigDecimal(papers.size()), 3, RoundingMode.HALF_UP)
            .multiply(new BigDecimal(100)));
    train.setDuration((int) (train.getEndTime().toEpochSecond(ZoneOffset.of("+8"))
        - train.getStartTime().toEpochSecond(ZoneOffset.of("+8"))));
    testPaperDao.save(papers);
    return PojoUtils.convertOne(termTrainDao.save(train), PostMilitaryTermTrainVO.class);
  }

  private PostMilitaryTermTrainEntity ownedTrain(String id, String token, boolean lock) {
    UserEntity user = userService.getUserByToken(token);
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("训练ID不能为空");
    }
    PostMilitaryTermTrainEntity entity = (lock
        ? termTrainDao.findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
        : termTrainDao.findByIdOptional(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(user.getId(), entity.getUserId(), "个人军语训练 " + id);
    return entity;
  }

  private Map<String, Map<String, String>> validateSubmission(
      List<PostMilitaryTermTrainTestPaperEntity> submitted,
      Map<String, PostMilitaryTermTrainTestPaperEntity> paperById) {
    Set<String> ids = new HashSet<>();
    Map<String, Map<String, String>> optionsByPaperId = new HashMap<>();
    for (PostMilitaryTermTrainTestPaperEntity answer : submitted) {
      if (answer == null || answer.getId() == null || answer.getId().isBlank()
          || !ids.add(answer.getId()) || !paperById.containsKey(answer.getId())) {
        throw new IllegalArgumentException("提交的题目无效或重复");
      }
      if (answer.getUserAnswer() == null) {
        continue;
      }
      PostMilitaryTermTrainTestPaperEntity paper = paperById.get(answer.getId());
      Map<String, String> options = parseOptions(paper);
      String correctAnswer = paper.getCorrectAnswer();
      if (correctAnswer == null || !options.containsKey(correctAnswer) || options.get(correctAnswer) == null
          || !options.containsKey(answer.getUserAnswer()) || options.get(answer.getUserAnswer()) == null) {
        throw new IllegalArgumentException("提交的答案无效");
      }
      optionsByPaperId.put(answer.getId(), options);
    }
    return optionsByPaperId;
  }

  private Map<String, String> parseOptions(PostMilitaryTermTrainTestPaperEntity paper) {
    Map<String, String> options;
    try {
      options = JSONUtils.fromJson(paper.getOption(), new TypeToken<>() {});
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("题目选项无效", exception);
    }
    if (options == null || options.isEmpty()) {
      throw new IllegalArgumentException("题目选项无效");
    }
    return options;
  }

  private boolean sameAnswers(List<PostMilitaryTermTrainTestPaperEntity> submitted,
                              Map<String, PostMilitaryTermTrainTestPaperEntity> paperById) {
    Map<String, String> submittedAnswers = new HashMap<>();
    for (PostMilitaryTermTrainTestPaperEntity answer : submitted) {
      submittedAnswers.put(answer.getId(), answer.getUserAnswer());
    }
    return paperById.entrySet().stream().allMatch(entry -> Objects.equals(
        entry.getValue().getUserAnswer(), submittedAnswers.get(entry.getKey())));
  }

  private PostMilitaryTermTrainTestPaperEntity paperForView(
      PostMilitaryTermTrainTestPaperEntity source, boolean completed) {
    return PojoUtils.convertOne(source, PostMilitaryTermTrainTestPaperEntity.class, (original, copy) -> {
      if (!completed) {
        copy.setCorrectAnswer(null);
      }
    });
  }

  /**
   * 删除个人军语训练。属主字段是 {@code userId}（各域字段名不同，这里显式传入）。
   */
  @Transactional
  public Boolean delete(String id, String token) {
    PostMilitaryTermTrainEntity entity = termTrainDao.findByIdOptional(id)
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    trainWriteAccess.requireTrainOwner(userService.getUserByToken(token).getId(), entity.getUserId(),
        "个人军语训练 " + id);
    testPaperDao.delete("trainId", id);
    return termTrainDao.deleteById(id);
  }
}
