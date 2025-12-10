package com.nip.service;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.constants.PostMilitaryTermTrainStatusEnum;
import com.nip.common.exception.NIPException;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.dao.PostMilitaryTermTrainDao;
import com.nip.dao.PostMilitaryTermTrainTestPaperDao;
import com.nip.dao.UserDao;
import com.nip.dto.PostMilitaryTermTrainAddDto;
import com.nip.dto.PostMilitaryTermTrainFinishDto;
import com.nip.dto.vo.PostMilitaryTermTrainVO;
import com.nip.entity.MilitaryTermDataEntity;
import com.nip.entity.PostMilitaryTermTrainEntity;
import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import com.nip.entity.UserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
  private final UserDao userDao;

  @Inject
  public PostMilitaryTermTrainService(PostMilitaryTermTrainDao termTrainDao, MilitaryTermDataDao dataDao,
                                      PostMilitaryTermTrainTestPaperDao testPaperDao, UserDao userDao) {
    this.termTrainDao = termTrainDao;
    this.dataDao = dataDao;
    this.testPaperDao = testPaperDao;
    this.userDao = userDao;
  }

  private final Random random = new Random();

  @Transactional
  public PostMilitaryTermTrainVO add(PostMilitaryTermTrainAddDto dto, String token) {
    try {
      UserEntity userEntity = userDao.findUserEntityByToken(token);

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
    } catch (Exception e) {
      e.printStackTrace();
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
        dataIndex = random.nextInt(dto.getTypes().size() - 1);
      }
      String dataId = dto.getTypes().get(dataIndex);
      //获得该类型的所有考题
      List<MilitaryTermDataEntity> militaryTermDataEntities = dataMap.get(dataId);
      if (militaryTermDataEntities == null) {
        i--;
        continue;
      }
      //考试题目
      int titleIndex;
      if (militaryTermDataEntities.size() == 1) {
        titleIndex = 0;
      } else {
        titleIndex = random.nextInt(militaryTermDataEntities.size() - 1);
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
      while (flag <= 3) {
        int optionId;
        if (militaryTermDataEntities.size() == 1) {
          optionId = 0;
        } else {
          //随机其它选项
          optionId = random.nextInt(militaryTermDataEntities.size() - 1);
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
        Integer rNumber = random.nextInt(9) + 1;
        while (rNumber.equals(Integer.valueOf(number))) {
          rNumber = random.nextInt(9) + 1;
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
    try {
      UserEntity userEntity = userDao.findUserEntityByToken(token);
      List<PostMilitaryTermTrainEntity> ret = termTrainDao.findByUserIdOrderByCreateTimeDesc(userEntity.getId());
      return PojoUtils.convert(ret, PostMilitaryTermTrainVO.class, (e, v) -> v.setTypes(
          dataDao.findAllByIdIn(JSONUtils.fromJson(e.getTypes(), new TypeToken<>() {
              }))
              .stream()
              .map(MilitaryTermDataEntity::getKey)
              .toList()));
    } catch (Exception e) {
      log.error("获取训练失败", e);
      return Collections.emptyList();
    }
  }

  public PostMilitaryTermTrainVO details(PostMilitaryTermTrainVO vo) {
    PostMilitaryTermTrainEntity entity = termTrainDao.findById(vo.getId());
    //查询试卷内容
    List<PostMilitaryTermTrainTestPaperEntity> testPaperEntities = testPaperDao.findAllByTrainId(vo.getId());

    return PojoUtils.convertOne(entity, PostMilitaryTermTrainVO.class, (e, v) -> {
      v.setTestPaperList(testPaperEntities);
      List<String> militaryTermDataIds = JSONUtils.fromJson(e.getTypes(), new TypeToken<>() {
      });
      List<MilitaryTermDataEntity> militaryTermDataEntities = dataDao.findAllByIdIn(militaryTermDataIds);
      List<String> names = militaryTermDataEntities.stream().map(MilitaryTermDataEntity::getKey).toList();
      v.setTypes(names);
    });
  }

  @Transactional(rollbackOn = Exception.class)
  public PostMilitaryTermTrainVO begin(String id) {
    PostMilitaryTermTrainEntity termTrainEntity = termTrainDao.findById(id);
    termTrainEntity.setStatus(PostMilitaryTermTrainStatusEnum.UNDERWAY.getStatus());
    termTrainEntity.setStartTime(LocalDateTime.now());
    return PojoUtils.convertOne(termTrainEntity, PostMilitaryTermTrainVO.class);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostMilitaryTermTrainVO finish(PostMilitaryTermTrainFinishDto dto) {
    PostMilitaryTermTrainEntity termTrainEntity = termTrainDao.findById(dto.getId());

    //状态设置成完成
    termTrainEntity.setStatus(PostMilitaryTermTrainStatusEnum.FINISH.getStatus());
    termTrainEntity.setEndTime(LocalDateTime.now());

    //查询出数据库中的考试题目
    Map<String, List<PostMilitaryTermTrainTestPaperEntity>> testPaperMap = testPaperDao.findAllByTrainId(
        termTrainEntity.getId()).stream().collect(Collectors.groupingBy(PostMilitaryTermTrainTestPaperEntity::getId));
    //用户提交答案
    List<PostMilitaryTermTrainTestPaperEntity> userTestPaperList = dto.getTestPaperList();

    int correctNum = 0;
    int errorNum = 0;
    int totalNum = 0;

    //设置正确答案,并统计正确错误个数
    for (PostMilitaryTermTrainTestPaperEntity testPaperEntity : userTestPaperList) {
      List<PostMilitaryTermTrainTestPaperEntity> entityList = testPaperMap.get(testPaperEntity.getId());
      if (entityList != null && !entityList.isEmpty()) {
        PostMilitaryTermTrainTestPaperEntity entity = entityList.getFirst();
        String userAnswer = testPaperEntity.getUserAnswer();
        entity.setUserAnswer(userAnswer);
        //统计已做题
        if (userAnswer != null) {
          totalNum++;
          //拿到答案字符串进行比对
          Map<String, String> map = JSONUtils.fromJson(entity.getOption(), new TypeToken<>() {
          });
          String userAnswerStr = map.get(userAnswer);
          String correctAnswerStr = map.get(entity.getCorrectAnswer());
          //判断答案是否正确
          if (Objects.equals(userAnswerStr, correctAnswerStr)) {
            correctNum++;
            // 当4个答案都是正确的时候，随便选择一个都是正确的，所以将选择的答案设置成正确答案
            entity.setCorrectAnswer(userAnswer);
          } else {
            errorNum++;
          }
        }
      }
      assert entityList != null;
      testPaperDao.save(entityList);
    }

    BigDecimal accuracy = new BigDecimal("0");
    BigDecimal score = new BigDecimal("0");
    if (totalNum > 0) {
      //计算正确率 correctNum / totalNum * 100
      accuracy = new BigDecimal(correctNum).divide(new BigDecimal(totalNum), 2, RoundingMode.HALF_UP)
          .multiply(new BigDecimal(100));

      //计算得分 correctNum / testPaperMap.size() *100 保留1位小数
      score = new BigDecimal(correctNum).divide(new BigDecimal(testPaperMap.size()), 3, RoundingMode.HALF_UP)
          .multiply(new BigDecimal(100));
    }
    termTrainEntity.setAccuracy(accuracy);
    termTrainEntity.setScore(score);
    termTrainEntity.setErrorNumber(errorNum);
    termTrainEntity.setCorrectNumber(correctNum);

    //训练时长
    long start = termTrainEntity.getStartTime().toEpochSecond(ZoneOffset.of("+8"));
    long end = termTrainEntity.getEndTime().toEpochSecond(ZoneOffset.of("+8"));
    termTrainEntity.setDuration((int) (end - start));

    //保存考题
    List<PostMilitaryTermTrainTestPaperEntity> testPaperEntityList = testPaperMap.values().stream()
        .flatMap(List::stream).toList();
    testPaperDao.save(testPaperEntityList);
    PostMilitaryTermTrainEntity save = termTrainDao.save(termTrainEntity);

    return PojoUtils.convertOne(save, PostMilitaryTermTrainVO.class);
  }

  @Transactional
  public Boolean delete(String id) {
    testPaperDao.delete("trainId", id);
    return termTrainDao.deleteById(id);
  }
}
