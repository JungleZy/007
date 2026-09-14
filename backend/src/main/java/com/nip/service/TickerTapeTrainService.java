package com.nip.service;

import cn.hutool.core.bean.BeanUtil;
import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.ForbiddenException;
import com.nip.common.PageInfo;
import com.nip.common.exception.TerminalStateException;
import com.nip.common.constants.TickerTapeTrainStatisticalTypeEnum;
import com.nip.common.constants.TickerTapeTrainStatusEnum;
import com.nip.common.constants.TickerTapeTrainTypeEnum;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.Page;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.TickerTapeTrainDao;
import com.nip.dao.TickerTapeTrainStatisticalDao;
import com.nip.dto.sql.TickerTapeTrainDaoCountBaseTrain;
import com.nip.dto.vo.TelexPatTrainStatisticalVO;
import com.nip.dto.vo.TickerTapeTrainVo;
import com.nip.dto.vo.param.TickerTapeBaseTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainAddParam;
import com.nip.dto.vo.param.TickerTapeTrainUpdateParam;
import com.nip.entity.TickerTapeTrainEntity;
import com.nip.entity.TickerTapeTrainStatisticalEntity;
import com.nip.entity.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @Author: wushilin
 * @Data: 2022-04-06 15:49
 * @Description:
 */
@ApplicationScoped
public class TickerTapeTrainService {

  private final TickerTapeTrainDao tickerTapeTrainDao;
  private final TickerTapeTrainStatisticalDao statisticalDao;
  private final UserService userService;

  // 使用构造函数注入依赖项
  @Inject
  public TickerTapeTrainService(TickerTapeTrainDao tickerTapeTrainDao, TickerTapeTrainStatisticalDao statisticalDao, UserService userService) {
    this.tickerTapeTrainDao = tickerTapeTrainDao;
    this.statisticalDao = statisticalDao;
    this.userService = userService;
  }

  @Transactional
  public TickerTapeTrainAddParam add(TickerTapeTrainAddParam param, String token) {
    // DATA-03：走 userService.getUserByToken，token 失效时抛 UnauthorizedException（200+code203），不再裸解引用 NPE
    UserEntity userEntity = userService.getUserByToken(token);
    String codeMessage = JSONUtils.toJson(param.getCodeMessageBody());
    TickerTapeTrainEntity entity = BeanUtil.toBean(param, TickerTapeTrainEntity.class);
    entity.setId(null);
    entity.setUserId(userEntity.getId());
    //状体设为未开始
    entity.setStatus(TickerTapeTrainStatusEnum.NOT_STARTED.getCode());
    entity.setCodeMessageBody(codeMessage);
    entity.setSchedule(0);
    //有效时长
    entity.setValidTime("0");
    entity.setProtocolVersion(1);
    entity.setAccumulatedActiveMillis(0L);
    //语音播放初始位置
    entity.setMark("0,0");
    entity.setCreateTime(LocalDateTime.now());
    //查询上一次训练状态
    TickerTapeTrainEntity lastTrain = tickerTapeTrainDao.lastTrain(userEntity.getId(), param.getType());
    if (lastTrain != null) {
      //如果状态是未开始，删除如是暂停则改为已完成，并统计
      if (Objects.equals(lastTrain.getStatus(), TickerTapeTrainStatusEnum.NOT_STARTED.getCode())) {
        tickerTapeTrainDao.deleteById(lastTrain.getId());
      } else if (Objects.equals(lastTrain.getStatus(), TickerTapeTrainStatusEnum.PAUSE.getCode())) {
        lastTrain.setStatus(TickerTapeTrainStatusEnum.FINISH.getCode());
        TickerTapeTrainEntity save = tickerTapeTrainDao.save(lastTrain);
        finishStatistical(save);
      }
    }
    TickerTapeTrainEntity save = tickerTapeTrainDao.save(entity);
    param.setId(save.getId());
    return param;
  }

  public PageInfo<TickerTapeTrainVo> listPage(Page page, String token) throws Exception {
    UserEntity userEntity = userService.getUserByToken(token);
    PanacheQuery<TickerTapeTrainEntity> query = tickerTapeTrainDao.find("userId = ?1 order by createTime desc",
        userEntity.getId()
    );
    query.page(io.quarkus.panache.common.Page.ofSize(page.getRows()));
    int totalPages = query.pageCount();
    long totalNumber = query.count();
    List<TickerTapeTrainEntity> entities = query.page(
        io.quarkus.panache.common.Page.of(page.getPage() - 1, page.getRows())).list();
    List<TickerTapeTrainVo> convert = PojoUtils.convert(entities, TickerTapeTrainVo.class, (e, v) -> {
      List<Map<String, Object>> maps = JSONUtils.fromJson(e.getCodeMessageBody(),
          new TypeToken<>() {
          });
      v.setCodeMessageBody(maps);
    });
    return new PageInfo<>(totalPages, totalNumber, (page.getPage()), page.getRows(), convert);
  }

  public TickerTapeTrainVo getById(String id, String token) {
    TickerTapeTrainEntity entity = owned(id, token);
    return PojoUtils.convertOne(entity, TickerTapeTrainVo.class, (e, v) -> {
      List<Map<String, Object>> maps = JSONUtils.fromJson(e.getCodeMessageBody(), new TypeToken<>() {});
      v.setCodeMessageBody(maps);
    });
  }

  @Transactional
  public void begin(String id, String token) {
    TickerTapeTrainEntity entity = ownedForUpdate(id, token);
    if (Objects.equals(entity.getStatus(), TickerTapeTrainStatusEnum.FINISH.getCode())) return;
    if (entity.getStartTime() == null) entity.setStartTime(LocalDateTime.now());
    if (!Objects.equals(entity.getStatus(), TickerTapeTrainStatusEnum.UNDERWAY.getCode())) {
      entity.setActiveSince(LocalDateTime.now());
      entity.setStatus(TickerTapeTrainStatusEnum.UNDERWAY.getCode());
    }
  }

  TickerTapeTrainVo getById(String id) {
    TickerTapeTrainEntity entity = Optional.ofNullable(tickerTapeTrainDao.findById(id)).orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    return PojoUtils.convertOne(entity, TickerTapeTrainVo.class);
  }

  @Transactional
  public void pause(TickerTapeTrainUpdateParam p, String token) {
    TickerTapeTrainEntity e = ownedForUpdate(p.getId(), token);
    if (Objects.equals(e.getStatus(), 3)) throw new TerminalStateException("训练已结束");
    if (Objects.equals(e.getStatus(), 2)) return;
    accrue(e); e.setValidTime(seconds(e)); e.setMark(p.getMark()); e.setSchedule(p.getSchedule());
    e.setStatus(2); e.setActiveSince(null); tickerTapeTrainDao.save(e);
  }

  @Transactional
  public void finish(TickerTapeTrainUpdateParam p, String token) {
    TickerTapeTrainEntity e = ownedForUpdate(p.getId(), token);
    if (Objects.equals(e.getStatus(), 3)) throw new TerminalStateException("训练已结束");
    accrue(e); e.setValidTime(seconds(e)); e.setMark(p.getMark()); e.setSchedule(p.getSchedule());
    e.setStatus(3); e.setEndTime(LocalDateTime.now()); e.setActiveSince(null); tickerTapeTrainDao.save(e);
    UserEntity owner = userService.getUserByToken(token);
    if (e.getType() == 21 || e.getType() == 22) saveStatistical(owner, e); else finishStatistical(e);
  }


  @Transactional
  public TickerTapeTrainVo createBaseSession(Integer type, String token) {
    UserEntity owner = userService.getUserByToken(token);
    if (type == null || (type != 21 && type != 22)) {
      throw new IllegalArgumentException("基础训练类型无效");
    }
    TickerTapeTrainEntity entity = new TickerTapeTrainEntity();
    entity.setUserId(owner.getId());
    entity.setType(type);
    entity.setStatus(TickerTapeTrainStatusEnum.NOT_STARTED.getCode());
    entity.setProtocolVersion(1);
    entity.setActiveSince(null);
    entity.setAccumulatedActiveMillis(0L);
    entity.setValidTime("0");
    entity.setMark("0");
    entity.setSchedule(0);
    return PojoUtils.convertOne(tickerTapeTrainDao.save(entity), TickerTapeTrainVo.class);
  }
  @Transactional
  public void saveBaseTrain(TickerTapeBaseTrainAddParam param, String token) {
    if (param == null || param.getId() == null || param.getId().isBlank()) {
      throw new IllegalArgumentException("收报会话编号不能为空");
    }
    UserEntity owner = userService.getUserByToken(token);
    TickerTapeTrainEntity entity = ownedForUpdate(param.getId(), token);
    if (!Objects.equals(entity.getStatus(), TickerTapeTrainStatusEnum.FINISH.getCode())) {
      accrue(entity);
      entity.setValidTime(seconds(entity));
      entity.setMark(param.getMark());
      entity.setSchedule(param.getSchedule());
      entity.setStatus(TickerTapeTrainStatusEnum.FINISH.getCode());
      entity.setEndTime(LocalDateTime.now());
      entity.setActiveSince(null);
      tickerTapeTrainDao.save(entity);
      saveStatistical(owner, entity);
    }
  }
  @Transactional
  public void discardBaseSession(String id, String token) {
    TickerTapeTrainEntity entity = ownedForUpdate(id, token);
    if (Objects.equals(entity.getStatus(), TickerTapeTrainStatusEnum.NOT_STARTED.getCode())) {
      tickerTapeTrainDao.deleteById(id);
    }
  }
  @Transactional
  public void saveEntity(TickerTapeTrainEntity entity) {
    tickerTapeTrainDao.save(entity);
  }

  @Transactional
  public void saveStatistical(UserEntity userEntity, TickerTapeTrainEntity entity) {
    //统计科式/基础
    Map<String, Object> map = tickerTapeTrainDao.countBaseTrain(userEntity.getId(), entity.getType());
    TickerTapeTrainStatisticalEntity statisticalEntity = PojoUtils.convertOne(map, TickerTapeTrainStatisticalEntity.class);
    TickerTapeTrainStatisticalEntity queryStatisticalEntity;
    if (entity.getType().compareTo(TickerTapeTrainTypeEnum.COD.getCode()) == 0) {
      queryStatisticalEntity = statisticalDao.findByUserIdAndType(
          userEntity.getId(), TickerTapeTrainStatisticalTypeEnum.COD.getCode());
      statisticalEntity.setType(TickerTapeTrainStatisticalTypeEnum.COD.getCode());

    } else {
      queryStatisticalEntity = statisticalDao.findByUserIdAndType(
          userEntity.getId(), TickerTapeTrainStatisticalTypeEnum.BASE.getCode());
      statisticalEntity.setType(TickerTapeTrainStatisticalTypeEnum.BASE.getCode());
    }

    queryStatisticalEntity = Optional.ofNullable(queryStatisticalEntity).map(
        temp -> temp.setTotalTime(statisticalEntity.getTotalTime()).setAvgSpeed(statisticalEntity.getAvgSpeed())
            .setTotalCount(statisticalEntity.getTotalCount())).orElse(
        new TickerTapeTrainStatisticalEntity()
            .setUserId(userEntity.getId())
            .setTotalTime(statisticalEntity.getTotalTime())
            .setAvgSpeed(statisticalEntity.getAvgSpeed())
            .setTotalCount(statisticalEntity.getTotalCount())
            .setType(statisticalEntity.getType()));

    statisticalDao.save(queryStatisticalEntity);
  }

  @Transactional
  public List<TelexPatTrainStatisticalVO> statisticalPage(String token) {
    UserEntity userEntity = userService.getUserByToken(token);
    List<TickerTapeTrainStatisticalEntity> entities = statisticalDao.findByUserId(userEntity.getId());
    Map<Integer, List<TickerTapeTrainStatisticalEntity>> collect = entities.stream().collect(
        Collectors.groupingBy(TickerTapeTrainStatisticalEntity::getType));
    for (int i = 0; i < 3; i++) {
      if (collect.get(i) == null) {
        TickerTapeTrainStatisticalEntity entity = new TickerTapeTrainStatisticalEntity();
        entity.setType(i);
        entity.setUserId(userEntity.getId());
        entity.setTotalCount(0);
        entity.setAvgSpeed(new BigDecimal(0));
        entity.setTotalTime("0");
        statisticalDao.save(entity);
        entities.add(entity);
      }
    }
    List<TelexPatTrainStatisticalVO> convert = PojoUtils.convert(entities, TelexPatTrainStatisticalVO.class);
    // 显式按 type 排序（P2-17：原 Collections.swap(0,1) 依赖 DB 返回顺序，串位且 size<2 越界）
    convert.sort(Comparator.comparingInt(TelexPatTrainStatisticalVO::getType));
    return convert;
  }

  public TickerTapeTrainVo lastTrain(String token, Integer type) {
    UserEntity userEntity = userService.getUserByToken(token);
    TickerTapeTrainEntity entity = tickerTapeTrainDao.lastTrain(userEntity.getId(), type);
    if (entity == null) {
      return new TickerTapeTrainVo();
    }
    return PojoUtils.convertOne(entity, TickerTapeTrainVo.class);
  }

  private TickerTapeTrainEntity owned(String id, String token) {
    UserEntity user = userService.getUserByToken(token);
    TickerTapeTrainEntity entity = Optional.ofNullable(tickerTapeTrainDao.findById(id)).orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    if (!Objects.equals(entity.getUserId(), user.getId())) throw new ForbiddenException("无权访问他人训练");
    return entity;
  }

  private TickerTapeTrainEntity ownedForUpdate(String id, String token) {
    UserEntity user = userService.getUserByToken(token);
    TickerTapeTrainEntity entity = Optional.ofNullable(tickerTapeTrainDao.findForUpdate(id)).orElseThrow(() -> new IllegalArgumentException("未查询到训练"));
    if (!Objects.equals(entity.getUserId(), user.getId())) throw new ForbiddenException("无权访问他人训练");
    return entity;
  }

  public void goOn(String id, String token) { begin(id, token); }

  private void accrue(TickerTapeTrainEntity e) {
    long total = e.getAccumulatedActiveMillis() == null ? 0L : e.getAccumulatedActiveMillis();
    if (e.getActiveSince() != null) total += Math.max(0L, java.time.Duration.between(e.getActiveSince(), LocalDateTime.now()).toMillis());
    e.setAccumulatedActiveMillis(total);
  }

  private String seconds(TickerTapeTrainEntity e) {
    return String.valueOf((e.getAccumulatedActiveMillis() == null ? 0L : e.getAccumulatedActiveMillis()) / 1000L);
  }

  @Transactional
  public void finishStatistical(TickerTapeTrainEntity entity) {
    TickerTapeTrainDaoCountBaseTrain train = tickerTapeTrainDao.countLetterTrain(entity.getUserId());
    TickerTapeTrainStatisticalEntity letterCount = PojoUtils.convertOne(train, TickerTapeTrainStatisticalEntity.class);
    //先查询是否有记录
    TickerTapeTrainStatisticalEntity statisticalEntity = statisticalDao.findByUserIdAndType(
        entity.getUserId(), TickerTapeTrainStatisticalTypeEnum.LETTER.getCode());

    TickerTapeTrainStatisticalEntity trainStatisticalEntity = Optional.ofNullable(statisticalEntity)
        .map(temp -> temp.setTotalCount(letterCount.getTotalCount())
            .setTotalTime(letterCount.getTotalTime())
            .setAvgSpeed(letterCount.getAvgSpeed()))
        .orElse(new TickerTapeTrainStatisticalEntity()
            .setType(TickerTapeTrainStatisticalTypeEnum.LETTER.getCode())
            .setUserId(entity.getUserId())
            .setTotalCount(letterCount.getTotalCount())
            .setTotalTime(letterCount.getTotalTime())
            .setAvgSpeed(letterCount.getAvgSpeed())

        );
    statisticalDao.save(trainStatisticalEntity);
  }
}
