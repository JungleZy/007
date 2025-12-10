package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.PageInfo;
import com.nip.common.constants.BaseConstants;
import com.nip.common.constants.PostTickerTapeTrainStatusEnum;
import com.nip.common.constants.TickerTapeTrainStatusEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.PostTickerTapeTrainDao;
import com.nip.dao.PostTickerTapeTrainPageDao;
import com.nip.dao.PostTickerTapeTrainPageValueDao;
import com.nip.dto.vo.PostTickerTapeTrainPageVO;
import com.nip.dto.vo.PostTickerTapeTrainPageValueVO;
import com.nip.dto.vo.PostTickerTapeTrainVo;
import com.nip.dto.vo.param.PostTickerTapeTrainAddParam;
import com.nip.dto.vo.param.PostTickerTapeTrainUpdateParam;
import com.nip.dto.vo.param.PostTickerTapeTrainUploadResultParam;
import com.nip.entity.PostTickerTapeTrainEntity;
import com.nip.entity.PostTickerTapeTrainPageEntity;
import com.nip.entity.PostTickerTapeTrainPageValueEntity;
import com.nip.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.HAS_SCORE;
import static com.nip.common.constants.PostTickerTapeTrainStatusEnum.NOT_STARTED;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:49
 * @Description:
 */
@ApplicationScoped
public class PostTickerTapeTrainService {

  private final PostTickerTapeTrainDao tickerTapeTrainDao;
  private final UserService userService;
  private final PostTickerTapeTrainPageDao pageDao;
  private final PostTickerTapeTrainPageValueDao valueDao;
  private final CableFloorService cableFloorService;

  @Inject
  public PostTickerTapeTrainService(PostTickerTapeTrainDao tickerTapeTrainDao,
                                    UserService userService,
                                    PostTickerTapeTrainPageDao pageDao,
                                    PostTickerTapeTrainPageValueDao valueDao,
                                    CableFloorService cableFloorService) {
    this.tickerTapeTrainDao = tickerTapeTrainDao;
    this.userService = userService;
    this.pageDao = pageDao;
    this.valueDao = valueDao;
    this.cableFloorService = cableFloorService;
  }

  @Transactional
  public PostTickerTapeTrainAddParam add(PostTickerTapeTrainAddParam param, HttpServerRequest request) {
    String token = request.getHeader(BaseConstants.TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);
    String codeMessage = JSONUtils.toJson(param.getCodeMessageBody());
    PostTickerTapeTrainEntity entity = PojoUtils.convertOne(param, PostTickerTapeTrainEntity.class);
    entity.setUserId(userEntity.getId());
    //状体设为未开始
    entity.setStatus(NOT_STARTED.getCode());
    entity.setCodeMessageBody(codeMessage);
    //有效时长
    entity.setValidTime("0");
    entity.setMoreCode(0);
    entity.setMoreGroup(0);
    entity.setLackCode(0);
    entity.setLackGroup(0);
    entity.setErrorCode(0);
    PostTickerTapeTrainEntity save = tickerTapeTrainDao.saveAndFlush(entity);
    if (entity.getIsCable() == 0) {
      //生成报底
      Integer generateNumber = 200;
      if (param.getTotalNumber() < 200) {
        generateNumber = param.getTotalNumber();
      }
      int index = save.getType().compareTo(1) == 0 ? 65 : 0;
      generateMessageBody(generateNumber, 1, index, save);
    } else {
      List<List<List<String>>> cableFloor = cableFloorService.findCableFloor(param.getCableId(), null, param.getStartPage());
      int totalPage = param.getTotalNumber() / 100;
      cableFloor = cableFloor.subList(0, totalPage);
      List<PostTickerTapeTrainPageEntity> list = new ArrayList<>();
      int floorNumber = 1;
      for (List<List<String>> floor : cableFloor) {
        int sortIndex = 0;
        for (List<String> moresKey : floor) {
          if (moresKey == null) {
            continue;
          }
          PostTickerTapeTrainPageEntity pageEntity = new PostTickerTapeTrainPageEntity();
          pageEntity.setKey(String.join("", moresKey));
          pageEntity.setPageNumber(floorNumber);
          pageEntity.setSort(sortIndex);
          pageEntity.setTrainId(save.getId());
          list.add(pageEntity);
          sortIndex++;
        }
        floorNumber++;
      }
      pageDao.save(list);
    }

    param.setId(save.getId());
    return param;
  }

  public PageInfo<PostTickerTapeTrainVo> listPage(Page page, HttpServerRequest request) {
    String token = request.getHeader(BaseConstants.TOKEN);
    UserEntity userEntity = userService.getUserByToken(token);
    PanacheQuery<PostTickerTapeTrainEntity> pageInfo = tickerTapeTrainDao.find("userId = ?1 ", Sort.by("createTime").descending(), userEntity.getId())
        .page(page.getPage() - 1, page.getRows());
    return PojoUtils.convertPage(pageInfo, PostTickerTapeTrainVo.class);
  }

  public PostTickerTapeTrainVo getById(String id) {
    PostTickerTapeTrainEntity entity = Optional.ofNullable(tickerTapeTrainDao.findById(id))
        .orElse(new PostTickerTapeTrainEntity());

    //查询images
    List<PostTickerTapeTrainPageValueEntity> valueEntities = valueDao.findByTrainId(id);
    List<String> images = valueEntities.stream()
        .map(PostTickerTapeTrainPageValueEntity::getImage)
        .toList();

    return PojoUtils.convertOne(entity, PostTickerTapeTrainVo.class, (e, v) -> {
      //查询第一页的数据
      List<Map<String, Object>> maps = JSONUtils.fromJson(e.getCodeMessageBody(), new TypeToken<>() {
      });
      v.setCodeMessageBody(maps);
      v.setImages(images);
      v.setIsStartSign(null == e.getIsStartSign() ? 0 : e.getIsStartSign());
      if (null != entity.getIsCable() && entity.getIsCable() == 1) {
        v.setPageNumber(pageDao.findMaxPageNumber(entity.getId()));
        v.setTotalNumber(pageDao.find("trainId", entity.getId()).list().size());
      }
    });
  }

  @Transactional
  public void begin(String id) {
    checkStatus(id);
    tickerTapeTrainDao.begin(id);
  }

  @Transactional
  public void finish(PostTickerTapeTrainUpdateParam updateParam) {
    checkStatus(updateParam.getId());
    PostTickerTapeTrainEntity entity = tickerTapeTrainDao.findById(updateParam.getId());
    LocalDateTime startTime = entity.getStartTime();
    LocalDateTime endTime = LocalDateTime.now();
    entity.setEndTime(endTime);
    entity.setStatus(PostTickerTapeTrainStatusEnum.FINISH.getCode());
    long millis = Duration.between(startTime, endTime).toSeconds();
    entity.setValidTime(String.valueOf(millis));

    tickerTapeTrainDao.save(entity);
  }

  @Transactional
  public void reset(String id) {
    PostTickerTapeTrainEntity entity = tickerTapeTrainDao.findById(id);
    Optional.ofNullable(entity)
        .orElseThrow(() -> new IllegalArgumentException(BaseConstants.TRAINING_NOT_FOUND));
    //状态校验
    if (entity.getStatus().compareTo(TickerTapeTrainStatusEnum.NOT_STARTED.getCode()) == 0) {
      throw new IllegalArgumentException("训练状态不是未开始");
    }
    entity.setStatus(NOT_STARTED.getCode());
    entity.setStartTime(null);
    tickerTapeTrainDao.save(entity);
  }


  @Transactional
  public PostTickerTapeTrainVo uploadResult(PostTickerTapeTrainUploadResultParam param) {
    int score = 100;
    int moreGroup = 0;
    int lackGroup = 0;
    int errorNumber = 0;
    int moreCode = 0;
    int lackCode = 0;

    PostTickerTapeTrainEntity entity = tickerTapeTrainDao.findById(param.getId());

    for (int i = 0; i < param.getResult().size(); i++) {
      List<String> userPage = param.getResult().get(i);
      //查询出本页的内容
      List<PostTickerTapeTrainPageEntity> pageEntities = pageDao.findByTrainIdAndPageNumberOrderBySort(param.getId(), i + 1);
      for (int j = 0; j < userPage.size(); j++) {
        String group = userPage.get(j);
        //查询报底是否有是否有内容
        if (pageEntities.size() - 1 >= j) {
          PostTickerTapeTrainPageEntity pageEntity = pageEntities.get(j);//判断是否填报是否有内容
          if (!Objects.equals(pageEntity.getKey(), group)) {
            if (Objects.equals("", group)) {
              lackGroup++;
            } else if (group.length() > pageEntity.getKey().length()) {
              moreCode += Math.abs(group.length() - pageEntity.getKey().length());
            } else if (group.length() < pageEntity.getKey().length()) {
              lackCode += Math.abs(group.length() - pageEntity.getKey().length());
            } else {
              errorNumber++;
            }
          }
          pageEntity.setValue(group);

        } else {
          //如报底没有内容，但填写了报文内容，则是多组
          if (!Objects.equals("", group)) {
            moreGroup++;
          }
        }
      }
      PostTickerTapeTrainPageValueEntity valueEntity = new PostTickerTapeTrainPageValueEntity();
      valueEntity.setTrainId(param.getId());
      valueEntity.setPageNumber(i + 1);
      valueEntity.setValue(JSONUtils.toJson(userPage));
      valueEntity.setImage(param.getImages().get(i));
      valueDao.save(valueEntity);
    }
    Integer userTotalGroup = param.getResult().stream()
        .map(List::size)
        .reduce(Integer::sum)
        .orElse(0);

    if (userTotalGroup.compareTo(entity.getTotalNumber()) < 0) {
      lackGroup += Math.abs(userTotalGroup - entity.getTotalNumber());
    }

    //计算分数
    score = score - moreGroup - lackGroup - moreCode - lackCode - errorNumber;

    //保存分数
    entity.setScore(String.valueOf(score));
    entity.setErrorCode(errorNumber);
    entity.setMoreCode(moreCode);
    entity.setLackCode(lackCode);
    entity.setMoreGroup(moreGroup);
    entity.setLackGroup(lackGroup);
    entity.setStatus(HAS_SCORE.getCode());
    entity.setResult(JSONUtils.toJson(param.getResult()));
    PostTickerTapeTrainEntity save = tickerTapeTrainDao.saveAndFlush(entity);
    return PojoUtils.convertOne(save, PostTickerTapeTrainVo.class, (t, v) -> v.setImages(param.getImages()));
  }

  public PostTickerTapeTrainPageValueVO findPage(String trainId, Integer pageNumber) {
    PostTickerTapeTrainEntity trainEntity = Optional.ofNullable(tickerTapeTrainDao.findById(trainId))
        .orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    //判断页码是否正确
    Integer totalNumber = trainEntity.getTotalNumber();
    int totalPage = totalNumber / 100;
    if (totalNumber % 100 > 0) {
      totalPage += 1;
    }
    if (pageNumber <= 0) {
      throw new IllegalArgumentException("页码不正确");
    }

    //先查询是否有内容
    List<PostTickerTapeTrainPageEntity> pageEntities = pageDao.findByTrainIdAndPageNumberOrderBySort(trainEntity.getId(), pageNumber);
    //查询是由有填报内容
    List<String> value = Optional.ofNullable(valueDao.findByTrainIdAndPageNumber(trainId, pageNumber))
        .map(PostTickerTapeTrainPageValueEntity::getValue)
        .map(v -> JSONUtils.fromJson(v, new TypeToken<List<String>>() {
        }))
        .orElseGet(ArrayList::new);

    if (pageEntities.isEmpty() && pageNumber <= totalPage) {
      int generateNumber = 100;
      if (pageNumber == totalPage) {
        generateNumber = totalNumber - ((pageNumber - 1) * 100);
      }
      //根据类型找出上一次最后一个字符
      int index = 0;
      if (trainEntity.getIsRandom().compareTo(0) == 0 || trainEntity.getIsAvg().compareTo(1) == 0) {
        if (trainEntity.getType().compareTo(1) == 0) {
          index = ((pageNumber - 1) * 400) % 26 + 65;
        } else if (trainEntity.getType().compareTo(2) == 0) {
          index = ((pageNumber - 1) * 400) % 36;
        }
      }
      pageEntities = generateMessageBody(generateNumber, pageNumber, index, trainEntity);
    }
    List<PostTickerTapeTrainPageVO> pageVo = PojoUtils.convert(pageEntities, PostTickerTapeTrainPageVO.class);
    PostTickerTapeTrainPageValueVO ret = new PostTickerTapeTrainPageValueVO();
    ret.setMessageBody(pageVo);
    ret.setValue(value);
    return ret;
  }

  private void checkStatus(String id) {
    PostTickerTapeTrainEntity entity = tickerTapeTrainDao.findById(id);
    if (entity.getStatus().compareTo(TickerTapeTrainStatusEnum.FINISH.getCode()) == 0) {
      throw new IllegalArgumentException("训练已结束");
    }
  }

  @Transactional
  public boolean delete(String trainId) {
    valueDao.delete("trainId=?1", trainId);
    pageDao.delete("trainId=?1", trainId);
    return tickerTapeTrainDao.deleteById(trainId);
  }

  /**
   * 生成报底
   *
   * @param generateNumber 报底
   * @param pageNumber     页码
   * @param index          上次位置
   * @param train          训练对象
   */
  @Transactional
  public List<PostTickerTapeTrainPageEntity> generateMessageBody(Integer generateNumber, Integer pageNumber, int index, PostTickerTapeTrainEntity train) {
    List<PostTickerTapeTrainPageEntity> ret = new ArrayList<>();
    int pageNum = pageNumber;
    Integer isAvg = train.getIsAvg();
    Integer isRandom = train.getIsRandom();
    Random random = new Random();
    List<String> avgB = new ArrayList<>();
    switch (train.getType()) {
      //数字报
      case 0:
        int numberIndex = 0;
        for (int i = 0; i < generateNumber; i++) {
          if (i % 100 == 0 && i != 0) {
            pageNumber += 1;
          }
          StringBuilder body = new StringBuilder();
          if (isAvg.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              avgB.add(String.valueOf(numberIndex));
              if (numberIndex == 9) {
                numberIndex = 0;
              } else {
                numberIndex++;
              }
            }
          } else if (isRandom.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              int i1 = random.nextInt(10);
              body.append(i1);
            }
          } else {
            for (int j = 0; j < 4; j++) {
              body.append(numberIndex);
              if (numberIndex == 9) {
                numberIndex = 0;
              } else {
                numberIndex++;
              }
            }
          }
          if (train.getIsAvg().compareTo(1) != 0) {
            PostTickerTapeTrainPageEntity pageEntity = new PostTickerTapeTrainPageEntity();
            pageEntity.setKey(body.toString());
            pageEntity.setPageNumber(pageNumber);
            pageEntity.setSort(i % 100);
            pageEntity.setTrainId(train.getId());
            ret.add(pageEntity);
          }
        }
        break;
      //字码报
      case 1:
        int charIndex = index;
        for (int i = 0; i < generateNumber; i++) {
          if (i % 100 == 0 && i != 0) {
            pageNumber += 1;
          }
          StringBuilder body = new StringBuilder();
          if (isAvg.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              char c = (char) charIndex;
              if (charIndex == 90) {
                charIndex = 65;
              } else {
                charIndex++;
              }
              avgB.add(String.valueOf(c));
            }
          } else if (isRandom.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              int i1 = random.nextInt(26) + 65;
              char a = (char) i1;
              body.append(a);
            }
          } else {
            for (int j = 0; j < 4; j++) {
              char c = (char) charIndex;
              if (charIndex == 90) {
                charIndex = 65;
              } else {
                charIndex++;
              }
              body.append(c);
            }
          }
          if (train.getIsAvg().compareTo(1) != 0) {
            PostTickerTapeTrainPageEntity pageEntity = new PostTickerTapeTrainPageEntity();
            pageEntity.setKey(body.toString());
            pageEntity.setPageNumber(pageNumber);
            pageEntity.setSort(i % 100);
            pageEntity.setTrainId(train.getId());
            ret.add(pageEntity);
          }
        }
        break;
      //混合报
      case 2:
        int charAndNumberIndex = index;
        for (int i = 0; i < generateNumber; i++) {
          StringBuilder body = new StringBuilder();
          if (isAvg.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              char c;
              if (charAndNumberIndex < 10) {
                c = (char) (charAndNumberIndex + 48);
              } else {
                c = (char) (charAndNumberIndex + 55);
              }
              if (charAndNumberIndex == 35) {
                charAndNumberIndex = 0;
              } else {
                charAndNumberIndex++;
              }
              avgB.add(String.valueOf(c));
            }
          } else if (isRandom.compareTo(1) == 0) {
            for (int j = 0; j < 4; j++) {
              int i1 = random.nextInt(36);
              if (i1 < 10) {
                i1 = (char) (i1 + 48);
              } else {
                i1 = (char) (i1 + 55);
              }
              char c = (char) i1;
              body.append(c);
            }
          } else {
            for (int j = 0; j < 4; j++) {
              char c;
              if (charAndNumberIndex < 10) {
                c = (char) (charAndNumberIndex + 48);
              } else {
                c = (char) (charAndNumberIndex + 55);
              }
              if (charAndNumberIndex == 35) {
                charAndNumberIndex = 0;
              } else {
                charAndNumberIndex++;
              }
              body.append(c);
            }
          }
          if (train.getIsAvg().compareTo(1) != 0) {
            if (i % 100 == 0 && i != 0) {
              pageNumber += 1;
            }
            PostTickerTapeTrainPageEntity pageEntity = new PostTickerTapeTrainPageEntity();
            pageEntity.setKey(body.toString());
            pageEntity.setPageNumber(pageNumber);
            pageEntity.setSort(i % 100);
            pageEntity.setTrainId(train.getId());
            ret.add(pageEntity);
          }
        }
        break;
      default:
        throw new IllegalArgumentException("未知类型");
    }
    if (train.getIsAvg().compareTo(1) == 0) {
      Collections.shuffle(avgB);
      int sort = 1;
      StringBuilder body = new StringBuilder();
      for (int i = 0; i < avgB.size(); i++) {
        body.append(avgB.get(i));
        if (i != 0 && i % 400 == 0) {
          pageNum++;
        }
        if (body.length() == 4) {
          PostTickerTapeTrainPageEntity pageEntity = new PostTickerTapeTrainPageEntity();
          pageEntity.setKey(body.toString());
          pageEntity.setPageNumber(pageNum);
          pageEntity.setSort(sort % 100);
          pageEntity.setTrainId(train.getId());
          ret.add(pageEntity);
          sort++;
          body = new StringBuilder();
        }
      }
    }
    return pageDao.save(ret);
  }
}
