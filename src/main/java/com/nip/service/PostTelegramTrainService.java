package com.nip.service;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.GlobalMessageGeneratedUtil;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.GradingRuleDao;
import com.nip.dao.PostTelegramTrainContentValueDao;
import com.nip.dao.PostTelegramTrainDao;
import com.nip.dao.PostTelegramTrainFloorContentDao;
import com.nip.dto.PostTelegramTrainContentValueDto;
import com.nip.dto.PostTelegramTrainFinishDto;
import com.nip.dto.PostTelegramTrainFinishInfoDto;
import com.nip.dto.score.PostTelegramTrainRule;
import com.nip.dto.score.SpeedDeduct;
import com.nip.dto.vo.*;
import com.nip.dto.vo.param.PostTelegramTrainAddParam;
import com.nip.dto.vo.param.PostTelegramTrainContentAddParam;
import com.nip.dto.vo.param.PostTelegramTrainFloorContentQueryParam;
import com.nip.dto.vo.param.PostTelegramTrainQueryParam;
import com.nip.entity.*;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.nip.common.constants.PostTelegramTrainEnum.*;
import static com.nip.common.constants.PostTelegramTrainTypeEnum.NUMBER_MESSAGE;
import static com.nip.common.constants.PostTelegramTrainTypeEnum.STRING_MESSAGE;
import static com.nip.common.utils.TickerPatUtils.*;
import static com.nip.common.utils.ToolUtil.calculateScore;
import static com.nip.common.utils.ToolUtil.calculateTS;
import static com.nip.service.general.GeneralKeyPatService.REGEX;

/**
 * @Author: wushilin
 * @Data: 2022-05-05 11:39
 * @Description:
 */

@Slf4j
@ApplicationScoped
public class PostTelegramTrainService {
  /**
   * 混合报
   */
  private final Map<String, String> mixture = new HashMap<>();
  private final Map<String, String> sortNumber = new HashMap<>();

  /**
   * 初始划 morse码和数字和字母的映射
   */
  @PostConstruct
  public void initMap() {
    mixture.put("01", "A");
    mixture.put("1000", "B");
    mixture.put("1010", "C");
    mixture.put("100", "D");
    mixture.put("0", "E");
    mixture.put("0010", "F");
    mixture.put("110", "G");
    mixture.put("0000", "H");
    mixture.put("00", "I");
    mixture.put("0111", "J");
    mixture.put("101", "K");
    mixture.put("0100", "L");
    mixture.put("11", "M");
    mixture.put("10", "N");
    mixture.put("111", "O");
    mixture.put("0110", "P");
    mixture.put("1101", "Q");
    mixture.put("010", "R");
    mixture.put("000", "S");
    mixture.put("1", "T");
    mixture.put("001", "U");
    mixture.put("0001", "V");
    mixture.put("011", "W");
    mixture.put("1001", "X");
    mixture.put("1011", "Y");
    mixture.put("1100", "Z");
    mixture.put("11111", "0");
    mixture.put("01111", "1");
    mixture.put("00111", "2");
    mixture.put("00011", "3");
    mixture.put("00001", "4");
    mixture.put("00000", "5");
    mixture.put("10000", "6");
    mixture.put("11000", "7");
    mixture.put("11100", "8");
    mixture.put("11110", "9");

    // 数字短码
    sortNumber.put("1", "0");
    sortNumber.put("01", "1");
    sortNumber.put("001", "2");
    sortNumber.put("00011", "3");
    sortNumber.put("00001", "4");
    sortNumber.put("00000", "5");
    sortNumber.put("10000", "6");
    sortNumber.put("11000", "7");
    sortNumber.put("100", "8");
    sortNumber.put("10", "9");
  }

  private static final String EMPTY_JSON_ARRAY = "[]";

  private final PostTelegramTrainDao postTelegramTrainDao;
  private final UserService userService;
  private final PostTelegramTrainFloorContentDao floorContentDao;
  private final PostTelegramTrainContentValueDao contentValueDao;
  private final GradingRuleDao gradingRuleDao;
  private final AsyncSavePostTelegramTrainService asyncSavePostTelegramTrainService;
  private final CableFloorService cableFloorService;
  private final MessageComparisonService messageComparisonService;

  @Inject
  public PostTelegramTrainService(PostTelegramTrainDao postTelegramTrainDao,
                                  UserService userService,
                                  PostTelegramTrainFloorContentDao floorContentDao,
                                  PostTelegramTrainContentValueDao contentValueDao,
                                  GradingRuleDao gradingRuleDao,
                                  AsyncSavePostTelegramTrainService asyncSavePostTelegramTrainService,
                                  CableFloorService cableFloorService,
                                  MessageComparisonService messageComparisonService) {
    this.postTelegramTrainDao = postTelegramTrainDao;
    this.userService = userService;
    this.floorContentDao = floorContentDao;
    this.contentValueDao = contentValueDao;
    this.gradingRuleDao = gradingRuleDao;
    this.asyncSavePostTelegramTrainService = asyncSavePostTelegramTrainService;
    this.cableFloorService = cableFloorService;
    this.messageComparisonService = messageComparisonService;
  }

  @Transactional
  public PostTelegramTrainVO save(PostTelegramTrainAddParam param, String token) {
    // 从token中获取用户
    UserEntity userEntity = userService.getUserByToken(token);
    PostTelegramTrainEntity entity = PojoUtils.convertOne(param, PostTelegramTrainEntity.class, (t, r) -> {
      // 初始速度是0
      r.setSpeed("0");
      // 默认状态为未开始
      r.setStatus(NOT_STARTED.getStatus());
      // 有效时间设置为0
      r.setValidTime(0L);
      r.setCreateUser(userEntity.getId());
      // 设置长短码和是否随机
      r.setCodeSort(Boolean.TRUE.equals(t.getCodeSort()) ? 1 : 0);
      r.setIsRandom(Boolean.TRUE.equals(t.getIsRandom()) ? 1 : 0);
      r.setIsAverage(Boolean.TRUE.equals(t.getIsAverage()) ? 1 : 0);
      // 错误个数默认为0
      r.setErrorNumber(0);
      // 正确率默认为0
      r.setAccuracy("0.00");
    });
    PostTelegramTrainEntity save = postTelegramTrainDao.saveAndFlush(entity);

    // 生成报文
    if (param.getIsCable() == 0) {
      Integer messageNumber = param.getMessageNumber();
      int generate;
      if (messageNumber > 200) {
        generate = 200;
      } else {
        generate = messageNumber;
      }
      Integer type = param.getType();

      List<PostTelegramTrainFloorContentEntity> ret = new ArrayList<>();
      List<String> messageBody;
      // 生成对应的报文
      if (type.compareTo(NUMBER_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedNumber(generate, param.getIsAverage(), param.getIsRandom());
      } else if (type.compareTo(STRING_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedWord(generate, param.getIsAverage(), param.getIsRandom());
      } else {
        messageBody = GlobalMessageGeneratedUtil.generatedMingle(generate, param.getIsAverage(), param.getIsRandom());
      }
      int floorNumber = 0;
      for (int i = 0; i < messageBody.size(); i++) {
        if (i % 100 == 0) {
          floorNumber++;
        }
        String group = messageBody.get(i);
        List<String> keys = new ArrayList<>();
        for (int j = 0; j < group.length(); j++) {
          keys.add(String.valueOf(group.charAt(j)));
        }
        PostTelegramTrainFloorContentEntity contentEntity = handleContentEntity(entity, floorNumber, i % 100, keys);
        ret.add(contentEntity);
      }

      floorContentDao.save(ret);
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null,
          param.getStartPage());
      int totalPage = param.getMessageNumber() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      List<PostTelegramTrainFloorContentEntity> list = new ArrayList<>();
      int floorNumber = 1;
      for (List<List<String>> floor : cableFloor) {
        int sortIndex = 0;
        for (List<String> moresKey : floor) {
          if (moresKey == null) {
            continue;
          }
          PostTelegramTrainFloorContentEntity contentEntity = handleContentEntity(entity, floorNumber, sortIndex,
              moresKey);
          list.add(contentEntity);
          sortIndex++;
        }
        floorNumber++;
      }
      floorContentDao.save(list);
    }
    return PojoUtils.convertOne(save, PostTelegramTrainVO.class);
  }

  private PostTelegramTrainFloorContentEntity handleContentEntity(PostTelegramTrainEntity entity,
                                                                  int floorNumber,
                                                                  int sortNumber,
                                                                  List<String> moresKey) {
    PostTelegramTrainFloorContentEntity contentEntity = new PostTelegramTrainFloorContentEntity();
    contentEntity.setTrainId(entity.getId());
    contentEntity.setMoresValue(EMPTY_JSON_ARRAY);
    contentEntity.setMoresTime(EMPTY_JSON_ARRAY);
    contentEntity.setPatKeys(EMPTY_JSON_ARRAY);
    contentEntity.setMoresKey(JSONUtils.toJson(moresKey));
    contentEntity.setFloorNumber(floorNumber);
    contentEntity.setSort(sortNumber);
    return contentEntity;
  }

  /**
   * 生成报文
   *
   * @param param   训练参数
   * @param trainId 训练id
   */
  private List<PostTelegramTrainFloorContentEntity> generateMessage(PostTelegramTrainEntity param,
                                                                    Integer generateNumber, int index, int floorNumber,
                                                                    String trainId) {
    List<PostTelegramTrainFloorContentEntity> floorContentEntities = new ArrayList<>();
    // 是否随机
    if (param.getIsRandom().compareTo(1) == 0) {
      // 判断是否平均
      if (param.getIsAverage().compareTo(1) == 0) {
        List<String> msg = new ArrayList<>();
        if (param.getType().compareTo(NUMBER_MESSAGE.getType()) == 0) {
          Random r = new Random();
          List<Integer> intArray = new ArrayList<>();
          int item = 0;
          for (int i = 0; i < generateNumber * 4; i++) {
            if (item == 10) {
              item = 0;
            }
            intArray.add(item);
            item++;
            int controllerNum = 0;
            if (intArray.size() % 40 == 0 || i == generateNumber * 4 - 1) {
              // 达到10组或最后一个之后，进行随机分配
              StringBuilder sb = new StringBuilder();
              int intArraySize = intArray.size();
              for (int j = 0; j < intArraySize; j++) {
                while (true) {
                  int indexR = r.nextInt(intArray.size());
                  Integer element = intArray.get(indexR);
                  // 获取上一个数字，所在区间1-5 6-0
                  boolean b;
                  if (!sb.isEmpty()) {
                    int lastNum = Integer.parseInt(String.valueOf(sb.charAt(sb.length() - 1)));
                    if (lastNum >= 1 && lastNum <= 5) {
                      // 本次元素必须是 6-7-8-9-0
                      b = (element >= 6 && element <= 9) || (element == 0);
                    } else {
                      // 本次元素必须是 1-2-3-4-5
                      b = element >= 1 && element <= 5;
                    }
                  } else {
                    b = true;
                  }
                  // 如果等于-1则说明没有该字符串，可以加入到sb中，且从intArray中移除
                  if (sb.indexOf(String.valueOf(element)) == -1 && b) {
                    sb.append(intArray.remove(indexR));
                    break;
                  }
                  // 结束死循环
                  if (controllerNum > 10 && intArray.size() <= 2) {
                    relieveWhile(msg, intArray, sb);
                  }
                  controllerNum++;
                }
                if (sb.length() == 4) {
                  msg.add(sb.toString());
                  sb = new StringBuilder();
                }
              }
              // 清空数组
              intArray.clear();
            }
          }
          // msg
          int floor = 0;
          int sort = 0;
          for (int i = 0; i < msg.size(); i++) {
            String s = msg.get(i);
            List<String> m = new ArrayList<>();
            for (int j = 0; j < s.length(); j++) {
              m.add(String.valueOf(s.charAt(j)));
            }
            if (i % 100 == 0) {
              floor++;
            }
            PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
            entity.setMoresValue(EMPTY_JSON_ARRAY);
            entity.setMoresTime(EMPTY_JSON_ARRAY);
            entity.setPatKeys(EMPTY_JSON_ARRAY);
            entity.setTrainId(trainId);
            entity.setFloorNumber(floor);
            entity.setSort(sort % 100);
            entity.setMoresKey(JSONUtils.toJson(m));
            floorContentEntities.add(entity);
            sort++;
          }
          return floorContentDao.saveAndFlush(floorContentEntities);
        } else if (param.getType().compareTo(STRING_MESSAGE.getType()) == 0) {
          int a = 65;
          for (int i = 0; i < generateNumber; i++) {
            for (int j = 0; j < 4; j++) {
              char number = (char) a;
              msg.add(String.valueOf(number));
              if (a == 90) {
                a = 65;
              } else {
                a += 1;
              }
            }
          }
        } else {
          int number = 48;
          for (int i = 0; i < generateNumber; i++) {
            for (int j = 0; j < 4; j++) {
              char c = (char) number;
              msg.add(String.valueOf(c));
              if (number >= 48 && number <= 57) {
                if (number == 57) {
                  number = 65;
                  continue;
                }
              } else {
                if (number == 90) {
                  number = 48;
                  continue;
                }
              }
              number += 1;
            }
          }
        }
        // 乱序
        Collections.shuffle(msg);
        List<String> group = new ArrayList<>();
        int sort = 0;
        for (int i = 0; i < msg.size(); i++) {
          String s = msg.get(i);
          group.add(s);
          if (i % 400 == 0) {
            floorNumber++;
          }
          if (group.size() == 4) {
            PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
            entity.setMoresValue(EMPTY_JSON_ARRAY);
            entity.setMoresTime(EMPTY_JSON_ARRAY);
            entity.setPatKeys(EMPTY_JSON_ARRAY);
            entity.setTrainId(trainId);
            entity.setFloorNumber(floorNumber);
            entity.setSort(sort % 100);
            entity.setMoresKey(JSONUtils.toJson(group));
            floorContentEntities.add(entity);

            group = new ArrayList<>();
            sort++;
          }
        }
      } else {
        Random numberRandom = new Random();
        if (param.getType().compareTo(NUMBER_MESSAGE.getType()) == 0) {
          for (int i = 0; i < generateNumber; i++) {
            List<String> msg = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
              int r = numberRandom.nextInt(10);
              msg.add(String.valueOf(r));
            }
            if (i % 100 == 0) {
              floorNumber++;
            }
            PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
            entity.setMoresValue(EMPTY_JSON_ARRAY);
            entity.setMoresTime(EMPTY_JSON_ARRAY);
            entity.setPatKeys(EMPTY_JSON_ARRAY);
            entity.setTrainId(trainId);
            entity.setFloorNumber(floorNumber);
            entity.setSort(i % 100);
            entity.setMoresKey(JSONUtils.toJson(msg));
            floorContentEntities.add(entity);
          }
        } else if (param.getType().compareTo(STRING_MESSAGE.getType()) == 0) {
          for (int i = 0; i < generateNumber; i++) {
            List<String> msg = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
              int r = numberRandom.nextInt(26);
              char s = (char) (r + 65);
              msg.add(String.valueOf(s));
            }
            if (i % 100 == 0) {
              floorNumber++;
            }
            PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
            entity.setMoresValue(EMPTY_JSON_ARRAY);
            entity.setMoresTime(EMPTY_JSON_ARRAY);
            entity.setPatKeys(EMPTY_JSON_ARRAY);
            entity.setTrainId(trainId);
            entity.setFloorNumber(floorNumber);
            entity.setSort(i % 100);
            entity.setMoresKey(JSONUtils.toJson(msg));
            floorContentEntities.add(entity);
          }
        } else {
          for (int i = 0; i < generateNumber; i++) {
            List<String> msg = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
              int r = numberRandom.nextInt(36);
              if (r > 9) {
                r = r + 55;
              } else {
                r = r + 48;
              }
              char s = (char) r;
              msg.add(String.valueOf(s));
            }
            if (i % 100 == 0) {
              floorNumber++;
            }
            PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
            entity.setMoresValue(EMPTY_JSON_ARRAY);
            entity.setMoresTime(EMPTY_JSON_ARRAY);
            entity.setPatKeys(EMPTY_JSON_ARRAY);
            entity.setTrainId(trainId);
            entity.setFloorNumber(floorNumber);
            entity.setSort(i % 100);
            entity.setMoresKey(JSONUtils.toJson(msg));
            floorContentEntities.add(entity);
          }
        }
      }
    } else {
      if (index > 90) {
        index = 48;
      } else if (index > 57 && index < 65) {
        index = 90;
      }
      // 根据类型生成报文
      if (param.getType().compareTo(NUMBER_MESSAGE.getType()) == 0) {
        int n = index;
        for (int i = 0; i < generateNumber; i++) {
          List<String> msg = new ArrayList<>();
          for (int j = 0; j < 4; j++) {
            char c = (char) n;
            msg.add(String.valueOf(c));
            if (n == 57) {
              n = 48;
            } else {
              n += 1;
            }
          }
          if (i % 100 == 0) {
            floorNumber++;
          }
          PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
          entity.setMoresValue(EMPTY_JSON_ARRAY);
          entity.setMoresTime(EMPTY_JSON_ARRAY);
          entity.setPatKeys(EMPTY_JSON_ARRAY);
          entity.setTrainId(trainId);
          entity.setFloorNumber(floorNumber);
          entity.setSort(i % 100);
          entity.setMoresKey(JSONUtils.toJson(msg));
          floorContentEntities.add(entity);
        }
      } else if (param.getType().compareTo(STRING_MESSAGE.getType()) == 0) {
        int a = index;
        for (int i = 0; i < generateNumber; i++) {
          List<String> msg = new ArrayList<>();
          for (int j = 0; j < 4; j++) {
            char c = (char) a;
            msg.add(String.valueOf(c));
            if (a == 90) {
              a = 65;
            } else {
              a += 1;
            }
          }
          if (i % 100 == 0) {
            floorNumber++;
          }
          PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
          entity.setMoresValue(EMPTY_JSON_ARRAY);
          entity.setMoresTime(EMPTY_JSON_ARRAY);
          entity.setPatKeys(EMPTY_JSON_ARRAY);
          entity.setTrainId(trainId);
          entity.setFloorNumber(floorNumber);
          entity.setSort(i % 100);
          entity.setMoresKey(JSONUtils.toJson(msg));
          floorContentEntities.add(entity);
        }
      } else {
        int number = index;
        for (int i = 0; i < generateNumber; i++) {
          List<String> msg = new ArrayList<>();
          for (int j = 0; j < 4; j++) {
            char c = (char) number;
            msg.add(String.valueOf(c));
            if (number >= 48 && number <= 57) {
              if (number == 57) {
                number = 65;
                continue;
              }
            } else {
              if (number == 90) {
                number = 48;
                continue;
              }
            }
            number += 1;
          }
          if (i % 100 == 0) {
            floorNumber++;
          }
          PostTelegramTrainFloorContentEntity entity = new PostTelegramTrainFloorContentEntity();
          entity.setMoresValue(EMPTY_JSON_ARRAY);
          entity.setMoresTime(EMPTY_JSON_ARRAY);
          entity.setPatKeys(EMPTY_JSON_ARRAY);
          entity.setTrainId(trainId);
          entity.setFloorNumber(floorNumber);
          entity.setSort(i % 100);
          entity.setMoresKey(JSONUtils.toJson(msg));
          floorContentEntities.add(entity);
        }
      }
    }
    return floorContentDao.saveAndFlush(floorContentEntities);
  }

  public List<PostTelegramTrainVO> findAll(String token) {
    // 从token中获取用户
    UserEntity userEntity = userService.getUserByToken(token);

    List<PostTelegramTrainEntity> entities = postTelegramTrainDao.find(
        "createUser = ?1", Sort.by("createTime").descending(), userEntity.getId()).list();
    return PojoUtils.convert(entities, PostTelegramTrainVO.class, (t, r) -> {
      r.setCodeSort(t.getCodeSort().compareTo(1) == 0);
      r.setIsRandom(t.getIsRandom().compareTo(1) == 0);
    });
  }

  public PostTelegramTrainVO detail(PostTelegramTrainQueryParam param) {
    PostTelegramTrainEntity entity = postTelegramTrainDao.findByIdOptional(param.getId())
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));

    return PojoUtils.convertOne(entity, PostTelegramTrainVO.class, (t, r) -> {
      // 判断报底是否为null
      if (t.getFloorNow() == null) {
        r.setFloorNow(1);
      }
      r.setCodeSort(t.getCodeSort().compareTo(1) == 0);
      r.setIsRandom(t.getIsRandom().compareTo(1) == 0);
      List<String> messageBody = new ArrayList<>();
      List<PostTelegramTrainFloorContentEntity> floorContentEntities = floorContentDao
          .findByTrainIdOrderByFloorNumberSort(entity.getId());
      List<Integer> floorNumber = floorContentDao.findByTrainIdCountFloor(entity.getId());

      r.setExistNumber(floorNumber);
      List<String> resolver = new ArrayList<>();

      Map<Integer, List<PostTelegramTrainFloorContentEntity>> collect = floorContentEntities.stream().collect(
          Collectors.groupingBy(PostTelegramTrainFloorContentEntity::getFloorNumber));
      List<PostTelegramTrainFinishInfoDto> finishInfoDtos = new ArrayList<>();
      List<String> standards = new ArrayList<>();
      for (Map.Entry<Integer, List<PostTelegramTrainFloorContentEntity>> entry : collect.entrySet()) {
        int next = entry.getKey();
        List<PostTelegramTrainFloorContentEntity> page = entry.getValue();
        PostTelegramTrainContentFloorValueEntity contentFloorValueEntity = contentValueDao.findByFloorNumberAndTrainId(
            next, param.getId());
        if (contentFloorValueEntity == null) {
          List<PostTelegramTrainContentAddParam> addParams = page.stream().map(
              e -> PojoUtils.convertOne(e, PostTelegramTrainContentAddParam.class)).toList();
          messageBody.add(JSONUtils.toJson(addParams));
          finishInfoDtos.add(null);
          standards.add("{}");
        } else {
          String finishInfo = contentFloorValueEntity.getFinishInfo();
          finishInfoDtos.add(JSONUtils.fromJson(finishInfo, PostTelegramTrainFinishInfoDto.class));
          messageBody.add(
              Optional.of(contentFloorValueEntity).map(PostTelegramTrainContentFloorValueEntity::getMessageBody)
                  .orElse(EMPTY_JSON_ARRAY));
          standards.add(contentFloorValueEntity.getStandard());

          resolver.add(contentFloorValueEntity.getResolver());
          if (messageBody.size() == 2) {
            break;
          }
        }
      }
      r.setResolver(resolver);
      r.setFinishInfo(finishInfoDtos);
      r.setMessageBody(messageBody);
      r.setStandards(standards);
      if (null != t.getIsCable() && t.getIsCable() == 1) {
        r.setMessageNumber(collect.size());
        r.setMessageGroup(floorContentEntities.size());
      }
    });
  }

  public PostTelegramTrainContentVO findMessageBody(PostTelegramTrainFloorContentQueryParam param) {
    PostTelegramTrainEntity entity = postTelegramTrainDao.findByIdOptional(param.getId())
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));

    List<PostTelegramTrainFloorContentEntity> contentEntities = floorContentDao.findByFloorNumberAndTrainIdOrderBySort(
        param.getFloorNumber(), param.getId());
    if (contentEntities.isEmpty()) {
      Integer currentPage = param.getFloorNumber();
      Integer messageNumber = entity.getMessageNumber();
      int totalPage = messageNumber / 100;
      int generateNumber = 0;
      if (messageNumber % 100 > 0) {
        totalPage += 1;
      }
      if (totalPage > currentPage) {
        generateNumber = 100;
      } else if (totalPage == currentPage) {
        generateNumber = messageNumber - ((currentPage - 1) * 100);
      }
      // 查询出上一页最后的值
      PostTelegramTrainFloorContentEntity floorContentEntity = floorContentDao
          .findByTrainIdOrderByFloorNumberDescSortDesc(param.getId());
      List<PostTelegramTrainFloorContentEntity> ret = new ArrayList<>();
      List<String> messageBody;
      // 生成对应的报文
      if (entity.getType().compareTo(NUMBER_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedNumber(
            generateNumber, entity.getIsAverage() == 1, entity.getIsRandom() == 1);
      } else if (entity.getType().compareTo(STRING_MESSAGE.getType()) == 0) {
        messageBody = GlobalMessageGeneratedUtil.generatedWord(
            generateNumber, entity.getIsAverage() == 1, entity.getIsRandom() == 1);
      } else {
        messageBody = GlobalMessageGeneratedUtil.generatedMingle(
            generateNumber, entity.getIsAverage() == 1, entity.getIsRandom() == 1);
      }
      int floorNumber = floorContentEntity.getFloorNumber();
      for (int i = 0; i < messageBody.size(); i++) {
        if (i % 100 == 0) {
          floorNumber++;
        }
        String group = messageBody.get(i);
        List<String> keys = new ArrayList<>();
        for (int j = 0; j < group.length(); j++) {
          keys.add(String.valueOf(group.charAt(j)));
        }
        PostTelegramTrainFloorContentEntity contentEntity = handleContentEntity(entity, floorNumber, i % 100, keys);
        ret.add(contentEntity);
      }
      contentEntities = floorContentDao.saveAndFlush(ret);
    }
    List<PostTelegramTrainContentAddParam> addParams = contentEntities.stream().map(
        e -> PojoUtils.convertOne(e, PostTelegramTrainContentAddParam.class)).toList();

    // 查询此页提交内容
    PostTelegramTrainContentFloorValueEntity contentFloorValueEntity = contentValueDao.findByFloorNumberAndTrainId(
        param.getFloorNumber(), param.getId());

    return new PostTelegramTrainContentVO(
        Optional.ofNullable(contentFloorValueEntity).map(PostTelegramTrainContentFloorValueEntity::getMessageBody)
            .orElseGet(() -> JSONUtils.toJson(addParams)),
        addParams,
        Optional.ofNullable(contentFloorValueEntity).map(PostTelegramTrainContentFloorValueEntity::getFinishInfo)
            .orElse(EMPTY_JSON_ARRAY),
        Optional.ofNullable(contentFloorValueEntity).map(PostTelegramTrainContentFloorValueEntity::getStandard)
            .orElse(EMPTY_JSON_ARRAY),
        Optional.ofNullable(contentFloorValueEntity).map(PostTelegramTrainContentFloorValueEntity::getResolver)
            .orElse(""));
  }

  @Transactional()
  public PostTelegramTrainVO begin(String id) {
    PostTelegramTrainEntity entity = Optional.ofNullable(postTelegramTrainDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    entity.setStartTime(LocalDateTime.now());
    entity.setStatus(UNDERWAY.getStatus());
    postTelegramTrainDao.saveAndFlush(entity);
    return PojoUtils.convertOne(entity, PostTelegramTrainVO.class);
  }

  @Transactional(rollbackOn = Exception.class)
  public void stop(String id) {
    PostTelegramTrainEntity entity = Optional.ofNullable(postTelegramTrainDao.findById(id))
        .orElseThrow(() -> new IllegalArgumentException("未查询到该训练"));
    // 判断状态是否是进行中
    if (entity.getStatus().compareTo(UNDERWAY.getStatus()) != 0) {
      throw new IllegalArgumentException(entity.getName() + "训练的状态不是进行中");
    }
    // 将状态修改成未开始，将报底修改为1
    entity.setStatus(NOT_STARTED.getStatus());
    entity.setFloorNow(1);
    entity.setSpeedLog(null);
    entity.setErrorNumber(0);
    entity.setAccuracy("0.00");
    // 清除本场训练的所有拍发内容
    contentValueDao.deleteByTrainId(id);
    // 清除floor content 内容
    floorContentDao.clearByTranId(id);
    postTelegramTrainDao.saveAndFlush(entity);
  }

  @Transactional(rollbackOn = Exception.class)
  public PostTelegramTrainVO finish(PostTelegramTrainFinishDto dto) {
    try {
      PostTelegramTrainEntity entity = Optional.ofNullable(postTelegramTrainDao.findById(dto.getId()))
          .orElseThrow(() -> new IllegalArgumentException("未查询到该场训练"));
      // 校验状态是否是进行中
      entity.setEndTime(LocalDateTime.now());
      entity.setStatus(FINISH.getStatus());
      entity.setValidTime(Long.valueOf(dto.getValidTime()));

      // 分数计算
      countScore(entity, dto);
      postTelegramTrainDao.saveAndFlush(entity);
      return PojoUtils.convertOne(entity, PostTelegramTrainVO.class, (t, r) -> {
        r.setCodeSort(t.getCodeSort().compareTo(1) == 0);
        r.setIsRandom(t.getIsRandom().compareTo(1) == 0);
      });
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }

  @Transactional(rollbackOn = Exception.class)
  public void saveContentValue(PostTelegramTrainContentValueDto dto) {
    PostTelegramTrainEntity trainEntity = Optional.ofNullable(postTelegramTrainDao.findById(dto.getTrainId()))
        .orElseThrow(() -> new IllegalArgumentException("未查询待该训练"));
    if (trainEntity.getMessageNumber().compareTo(dto.getFloorNumber()) > 0) {
      trainEntity.setFloorNow(dto.getFloorNumber() + 1);
    }
    // 记录每页速率
    List<String> speedLog = Optional.ofNullable(trainEntity.getSpeedLog())
        .map(speed -> JSONUtils.fromJson(speed, new TypeToken<List<String>>() {
        })).orElseGet(ArrayList::new);
    speedLog.add(dto.getSpeed());
    trainEntity.setSpeedLog(JSONUtils.toJson(speedLog));
    trainEntity.setErrorNumber(dto.getErrorNumber());
    trainEntity.setAccuracy(dto.getAccuracy());

    PostTelegramTrainContentFloorValueEntity valueEntity = PojoUtils.convertOne(
        dto, PostTelegramTrainContentFloorValueEntity.class, (d, e) -> {
          List<PostTelegramTrainContentAddParam> messageBody = d.getMessageBody();
          e.setMessageBody(JSONUtils.toJson(messageBody));
          List<PostTelegramTrainFinishInfoDto> standard = dto.getStandard();
          e.setStandard(JSONUtils.toJson(standard));
        });
    // 保存拍发速率
    postTelegramTrainDao.saveAndFlush(trainEntity);
    // 删除之前保存的训练记录
    contentValueDao.deleteByTrainIdAndFloorNumber(dto.getTrainId(), dto.getFloorNumber());
    contentValueDao.saveAndFlush(valueEntity);
  }

  public List<String> printBottomReport(PostTelegramTrainQueryParam param) {
    PostTelegramTrainEntity entity = postTelegramTrainDao.findByIdOptional(param.getId()).orElseThrow();
    PostTelegramTrainFloorContentEntity floorContentEntity = floorContentDao
        .findByTrainIdOrderByFloorNumberDescSortDesc(param.getId());
    Integer messageNumber = entity.getMessageNumber();
    int totalPage = messageNumber / 100;
    int floorNumber = 0;
    int generateNumber;
    if (messageNumber % 100 > 0) {
      totalPage += 1;
    }
    int pageSize = 10000;
    if (ObjectUtil.isNotEmpty(floorContentEntity)) {
      if (floorContentEntity.getFloorNumber().equals(totalPage)) {
        List<PostTelegramTrainFloorContentEntity> contentEntities = new ArrayList<>();
        List<Future<List<PostTelegramTrainFloorContentEntity>>> futures = new ArrayList<>();
        Integer count = floorContentDao.findCountByTrainIdOrderByFloorNumberAscSortAsc(param.getId());
        if (count <= pageSize) {
          contentEntities
              .addAll(floorContentDao.findAllByTrainIdOrderByFloorNumberAscSortAscLimit(param.getId(), 0, count));
        } else {
          int totalPage1 = count % pageSize == 0 ? count / pageSize : count / pageSize + 1;
          for (int i = 0; i < totalPage1; i++) {
            int index = i * pageSize;
            futures.add(
                asyncSavePostTelegramTrainService.selectPostTelegramTrainFloorContent(param.getId(), index, pageSize));
          }
        }
        futures.forEach(listFuture -> {
          try {
            contentEntities.addAll(listFuture.get());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复线程的中断状态
            log.warn("Thread was interrupted:", e);
          } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
              log.error("Execution failed due to: ", cause);
            } else {
              log.error("Execution failed", e);
            }
          }
        });
        return contentEntities.stream().map(PostTelegramTrainFloorContentEntity::getMoresKey).toList();
      }
      generateNumber = entity.getMessageNumber() - (floorContentEntity.getFloorNumber() * 100);
      floorNumber = floorContentEntity.getFloorNumber();
    } else {
      generateNumber = entity.getMessageNumber();
    }
    Integer type = entity.getType();
    int index = type.compareTo(STRING_MESSAGE.getType()) == 0 ? 65 : 48;
    List<PostTelegramTrainFloorContentEntity> contentEntityList = floorContentDao
        .findByTrainIdOrderByFloorNumberSort(param.getId());
    List<PostTelegramTrainFloorContentEntity> contentEntities = generateMessage(entity, generateNumber, index,
        floorNumber, param.getId());
    contentEntityList.addAll(contentEntities);
    return contentEntityList.stream().map(PostTelegramTrainFloorContentEntity::getMoresKey).toList();
  }

  /**
   * 计算分数
   *
   * @param entity
   * @param dto
   */
  private void countScore(PostTelegramTrainEntity entity, PostTelegramTrainFinishDto dto) {
    // 扣分Map，最后将其转成JSON存入到deduct_info 字段中
    Map<String, Integer> deductMap = new HashMap<>();

    // 得到评分规则
    GradingRuleEntity ruleEntity = gradingRuleDao.findByIdOptional(entity.getRuleId())
        .orElseThrow(() -> new IllegalArgumentException("评分规则不存在"));
    // 基础分数
    Integer score = ruleEntity.getScore();

    // 解析
    String content = ruleEntity.getContent();
    PostTelegramTrainRule rule = parseContent(content);

    // 多组或少组
    int moreOrLackGroup = 0;
    // 多字或少字
    int moreOrLackWord = 0;
    int dotScore = 0;
    int lineScore = 0;
    int codeScore = 0;
    int wordScore = 0;
    int groupScore = 0;
    int alterErrorScore = 0;
    int errorCode = 0;
    // 统计信息
    PostTelegramTrainStatisticsVO statisticsVO = new PostTelegramTrainStatisticsVO();
    PostTelegramTrainScoreVO scoreVO = new PostTelegramTrainScoreVO();

    // 查询出提交的页码数
    List<Integer> existFloorNumber = contentValueDao.countExistFloorNumber(entity.getId());
    if (dto.getFinishInfo().isEmpty()) {
      entity.setAccuracy("0.00");
      entity.setSpeed("0.00");
      entity.setScore("0");
      entity.setRuleContent(ruleEntity.getContent());
      entity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
      deductMap.put("dotMinScore", dotScore);
      deductMap.put("lineScore", lineScore);
      deductMap.put("codeGapScore", codeScore);
      deductMap.put("wordGapScore", wordScore);
      deductMap.put("groupGapScore", groupScore);
      deductMap.put("alterErrorScore", alterErrorScore);
      deductMap.put("errorWord", errorCode);
      deductMap.put("quantoGroup", moreOrLackGroup);
      deductMap.put("quantoCode", moreOrLackWord);
      deductMap.put("quantoRow", 0);
      deductMap.put("bunchGroup", 0);
      String deductMapInfo = JSONUtils.toJson(deductMap);
      entity.setDeductInfo(deductMapInfo);
      return;
    }
    for (Integer floorNumber : existFloorNumber) {
      // 查询提交的页内容
      PostTelegramTrainContentFloorValueEntity contentFloorValueEntity = contentValueDao.findByFloorNumberAndTrainId(
          floorNumber, entity.getId());
      String messageBody = contentFloorValueEntity.getMessageBody();
      String standard = contentFloorValueEntity.getStandard();
      // 拍发内容
      List<PostTelegramTrainContentAddParam> userContents = JSONUtils.fromJson(messageBody, new TypeToken<>() {
      });
      // 拍发此页的标准值
      List<PostTelegramTrainFinishInfoDto> standards = JSONUtils.fromJson(standard, new TypeToken<>() {
      });
      // 生成的标准内容
      List<PostTelegramTrainFloorContentEntity> floorContentEntities = floorContentDao
          .findByFloorNumberAndTrainIdOrderBySort(floorNumber, entity.getId());
      List<String> sources = floorContentEntities.stream().map(PostTelegramTrainFloorContentEntity::getMoresKey).map(
              item -> item.substring(1).replaceAll(REGEX, ""))
          .toList();
      List<String> patKeys = null;
      if (userContents != null) {
        patKeys = userContents.stream().map(PostTelegramTrainContentAddParam::getPatKeys).map(
                item -> item.substring(1).replaceAll(REGEX, ""))
            .toList();
      }
      PostTelegramTrainResolverVO comparison = messageComparisonService.comparison(
          sources, patKeys, scoreVO, userContents, standards, rule, statisticsVO);
      contentFloorValueEntity.setResolver(JSONUtils.toJson(comparison));
    }

    statisticsAllAvg(statisticsVO,
        scoreVO.getDotTotalTime(),
        scoreVO.getLineTotalTime(),
        scoreVO.getCodeTotalTime(),
        scoreVO.getWordTotalTime(),
        scoreVO.getGroupTotalTime());

    // 计算未生成和未拍的数量
    Integer messageNumber = entity.getMessageNumber();
    int totalFloorNumber = messageNumber / 100;
    if (messageNumber % 100 > 0) {
      totalFloorNumber++;
    }
    List<Integer> existPageNumber = new ArrayList<>();
    for (int i = 0; i < totalFloorNumber; i++) {
      existPageNumber.add(i + 1);
    }
    existPageNumber.removeAll(existFloorNumber);
    for (int i = 0; i < existPageNumber.size(); i++) {
      if (i != existPageNumber.size() - 1) {
        scoreVO.setLackGroup(scoreVO.getLackGroup() + 100);
      } else {
        scoreVO.setLackGroup(scoreVO.getLackGroup() + messageNumber - ((totalFloorNumber - 1) * 100));
      }
    }

    // 处理完进行扣分
    dotScore = calculateScore(rule.getDot().getMax(), scoreVO.getDotScore(), rule.getDot().getMax());
    score = score - dotScore;
    deductMap.put("dotMinScore", dotScore);
    deductMap.put("dotMinNumber", scoreVO.getDotScore());
    lineScore = calculateScore(rule.getDash().getMax(), scoreVO.getLineScore(), rule.getDot().getMax());
    score = score - lineScore;
    deductMap.put("lineScore", lineScore);
    deductMap.put("lineNumber", scoreVO.getLineScore());
    codeScore = calculateScore(rule.getLittle().getMax(), scoreVO.getCodeScore(), rule.getLittle().getMax());
    score = score - codeScore;
    deductMap.put("codeGapScore", codeScore);
    deductMap.put("codeNumber", scoreVO.getCodeScore());
    wordScore = calculateScore(rule.getMiddle().getMax(), scoreVO.getWordScore(), rule.getMiddle().getMax());
    score = score - wordScore;
    deductMap.put("wordGapScore", wordScore);
    deductMap.put("wordNumber", scoreVO.getWordScore());
    groupScore = calculateScore(rule.getLarge().getMax(), scoreVO.getGroupScore(), rule.getLarge().getMax());
    score = score - groupScore;
    deductMap.put("groupGapScore", groupScore);
    deductMap.put("groupNumber", scoreVO.getGroupScore());
    alterErrorScore = calculateScore(rule.getAlterError().getMax(), scoreVO.getAlterErrorScore(),
        rule.getAlterError().getMax());
    score = score - alterErrorScore;
    deductMap.put("alterErrorScore", alterErrorScore);
    deductMap.put("alterErrorNumber", scoreVO.getAlterErrorScore());

    // 扣分
    errorCode = calculateScore(rule.getErrorCode().getMax(), scoreVO.getErrorNumber() * rule.getErrorCode().getL(),
        rule.getErrorCode().getMax());
    score = score - errorCode;
    deductMap.put("errorWord", errorCode);
    deductMap.put("errorWordNumber", scoreVO.getErrorNumber());

    moreOrLackWord = calculateScore(rule.getQuantoCode().getMax(),
        scoreVO.getMoreOrLackWord() * rule.getQuantoCode().getL(), rule.getQuantoCode().getMax());
    score = score - moreOrLackWord;
    deductMap.put("quantoCode", moreOrLackWord);
    deductMap.put("quantoCodeNumber", scoreVO.getMoreOrLackWord());

    moreOrLackGroup = calculateScore(rule.getQuantoGroup().getMax(),
        (scoreVO.getMoreGroup() + scoreVO.getLackGroup()) * rule.getQuantoGroup().getL(),
        rule.getQuantoGroup().getMax());
    score = score - moreOrLackGroup;
    deductMap.put("quantoGroup", moreOrLackGroup);
    deductMap.put("quantoGroupNumber", scoreVO.getMoreGroup());

    // 多行少行
    int moreOrLackLine = calculateScore(rule.getQuantoRow().getMax(),
        scoreVO.getMoreOrLackLine() * rule.getQuantoRow().getL(), rule.getQuantoRow().getMax());
    score = score - moreOrLackLine;
    deductMap.put("quantoRow", moreOrLackLine);
    deductMap.put("quantoRowNumber", scoreVO.getMoreOrLackLine());

    // 串行
    int bunchGroup = calculateScore(rule.getBunchGroup().getMax(),
        scoreVO.getBunchGroup() * rule.getBunchGroup().getL(), rule.getBunchGroup().getMax());
    score = score - bunchGroup;
    deductMap.put("bunchGroup", bunchGroup);
    deductMap.put("bunchGroupNumber", scoreVO.getBunchGroup());

    // 设置
    entity.setErrorNumber(scoreVO.getErrorNumber());
    entity.setLack(scoreVO.getLackGroup());
    // 计算正确率,若是没有任何拍发记录则是0
    if (scoreVO.getCorrect() == 0) {
      entity.setAccuracy("0.00");
    } else {
      String accuracy = new BigDecimal(scoreVO.getCorrect()).divide(
          new BigDecimal(scoreVO.getPatTotalNum()),
          2,
          RoundingMode.HALF_UP).multiply(new BigDecimal(100)).toString();
      entity.setAccuracy(accuracy);
    }

    // 计算平均速率
    List<String> speedLog = Optional.ofNullable(entity.getSpeedLog())
        .map(speedLod -> JSONUtils.fromJson(speedLod, new TypeToken<List<String>>() {
        }))
        .orElseGet(ArrayList::new);

    // 判断速率是码每分还是WPM
    String speed = speedLog.stream()
        .map(BigDecimal::new)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(new BigDecimal(speedLog.size()), 0, RoundingMode.HALF_DOWN)
        .toString();
    entity.setSpeed(speed);

    // 计算wpm
    SpeedDeduct baseWpm = rule.getWpm();
    int wpm = baseWpm.getBase() - new BigDecimal(entity.getSpeed()).intValue();
    int wpmScore = (wpm > 0 ? -(wpm * baseWpm.getL()) : wpm * baseWpm.getR());
    deductMap.put("wpmScore", wpmScore);
    score = score + wpmScore;
    entity.setScore(String.valueOf(score));
    entity.setRuleContent(ruleEntity.getContent());
    entity.setStatisticInfo(JSONUtils.toJson(statisticsVO));
    String deductMapInfo = JSONUtils.toJson(deductMap);
    entity.setDeductInfo(deductMapInfo);
  }

  /**
   * 追加 content value
   *
   * @param vo
   */

  public List<Integer> addContentValue(PostTelegramTrainAddContentValueVO vo) {
    Integer floorNumber = 0;
    PostTelegramTrainFloorContentEntity entity = floorContentDao.findByTrainId(vo.getTrainId());
    if (!Objects.isNull(entity)) {
      floorNumber = entity.getFloorNumber();
    }
    List<List<PostTelegramTrainContentAddParam>> messageBody = vo.getMessageBody();
    for (int i = 0; i < messageBody.size(); i++) {
      floorNumber += i;
      List<PostTelegramTrainContentAddParam> addParams = messageBody.get(i);
      for (int j = 0; j < addParams.size(); j++) {
        PostTelegramTrainContentAddParam contentAddParam = addParams.get(j);
        Integer finalFloorNumber = floorNumber;
        int finalJ = j;
        PostTelegramTrainFloorContentEntity contentEntity = PojoUtils.convertOne(
            contentAddParam, PostTelegramTrainFloorContentEntity.class, (p, e) -> {
              e.setFloorNumber(finalFloorNumber);
              e.setSort(finalJ);
              e.setTrainId(vo.getTrainId());
              e.setMoresTime(EMPTY_JSON_ARRAY);
            });
        floorContentDao.saveAndFlush(contentEntity);
      }
    }
    // 查询已返回的FloorNumber
    return floorContentDao.findByTrainIdCountFloor(vo.getTrainId());
  }

  @Transactional
  public Boolean delete(String trainId) {
    contentValueDao.delete("trainId", trainId);
    floorContentDao.delete("trainId", trainId);
    return postTelegramTrainDao.deleteById(trainId);
  }

  /**
   * 对控制变量进行校验 在最后一组会出现，当intArray中的数字，与ret中相同，此时需要用之前的报文来替换。
   * 例如：ret = 81 intArray=83 就需要此段代码来解除死循环；找出前面的组将intArray中的8进行替换 替换后intArray = 63
   * 即可解除循环
   *
   * @param ret
   * @param intArray
   * @param sb
   */
  private void relieveWhile(List<String> ret, List<Integer> intArray, StringBuilder sb) {
    for (int z = 0; z < intArray.size(); z++) {
      Integer integer = intArray.get(z);
      if (sb.indexOf(String.valueOf(integer)) != -1) {

        for (int k = 1; k < 10; k++) {
          String lastGroup = ret.get(ret.size() - k);
          // 对上一组的进行判定是否包含本次重复字符串
          if (!lastGroup.contains(String.valueOf(integer))) {
            // 如果不包含进行替换本次重复字符串
            for (int l = 0; l < lastGroup.length(); l++) {
              int temp = Integer.parseInt(String.valueOf(lastGroup.charAt(l)));
              // 找出范围
              String replace = lastGroup.replace(String.valueOf(temp), String.valueOf(integer));
              if (integer >= 1 && integer <= 5) {
                if (temp >= 1 && temp <= 5) {
                  ret.set(ret.size() - k, replace);
                  intArray.set(z, temp);
                  break;
                }
              } else {
                if ((temp >= 6 && temp <= 9) || temp == 0) {
                  ret.set(ret.size() - k, replace);
                  intArray.set(z, temp);
                  break;
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * 统计所有点划和所有间隔的平均时长
   *
   * @param statisticsVO   统计对象
   * @param dotTotalTime   点
   * @param lineTotalTime  划
   * @param codeTotalTime  码
   * @param wordTotalTime  词
   * @param groupTotalTime 组
   */
  private void statisticsAllAvg(PostTelegramTrainStatisticsVO statisticsVO, int dotTotalTime, int lineTotalTime,
                                int codeTotalTime, int wordTotalTime, int groupTotalTime) {
    // 计算点划间隔的平均时长
    if (statisticsVO.getDotMaxNumber() + statisticsVO.getDotMinNumber() + statisticsVO.getDotPerfectNumber() != 0) {
      statisticsVO.setDotAvg(calculateTS(dotTotalTime, statisticsVO.getDotMaxNumber(), statisticsVO.getDotMinNumber(),
          statisticsVO.getDotPerfectNumber()));
    }
    if (statisticsVO.getLineMaxNumber() + statisticsVO.getLineMinNumber() + statisticsVO.getLinePerfectNumber() != 0) {
      statisticsVO.setLineAvg(calculateTS(lineTotalTime, statisticsVO.getLineMaxNumber(),
          statisticsVO.getLineMinNumber(), statisticsVO.getLinePerfectNumber()));
    }
    if (statisticsVO.getCodeMaxNumber() + statisticsVO.getCodeMinNumber() + statisticsVO.getCodePerfectNumber() != 0) {
      statisticsVO.setCodeAvg(calculateTS(codeTotalTime, statisticsVO.getCodeMaxNumber(),
          statisticsVO.getCodeMinNumber(), statisticsVO.getCodePerfectNumber()));
    }
    if (statisticsVO.getWordMaxNumber() + statisticsVO.getWordMinNumber() + statisticsVO.getWordPerfectNumber() != 0) {
      statisticsVO.setWordAvg(calculateTS(wordTotalTime, statisticsVO.getWordMaxNumber(),
          statisticsVO.getWordMinNumber(), statisticsVO.getWordPerfectNumber()));
    }
    if (statisticsVO.getGroupMaxNumber() + statisticsVO.getGroupMinNumber()
        + statisticsVO.getGroupPerfectNumber() != 0) {
      statisticsVO.setGroupAvg(calculateTS(groupTotalTime, statisticsVO.getGroupMaxNumber(),
          statisticsVO.getGroupMinNumber(), statisticsVO.getGroupPerfectNumber()));
    }
  }
}
