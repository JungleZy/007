package com.nip.service;

import cn.hutool.core.util.ObjectUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.nip.common.exception.NIPException;
import com.nip.common.utils.JSONUtils;
import com.nip.common.utils.PojoUtils;
import com.nip.dao.MilitaryTermDataDao;
import com.nip.dto.MilitaryTermDataDto;
import com.nip.dto.MilitaryTermDataMoveDto;
import com.nip.dto.MilitaryTermDto;
import com.nip.dto.vo.MilitaryTermDataVO;
import com.nip.entity.MilitaryTermDataEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;

/**
 * MilitaryTermDataService
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-06-23 14:54
 */
@ApplicationScoped
public class MilitaryTermDataService {
  private final MilitaryTermDataDao militaryTermDataDao;

  @Inject
  public MilitaryTermDataService(MilitaryTermDataDao militaryTermDataDao) {
    this.militaryTermDataDao = militaryTermDataDao;
  }

  /**
   * 获取一级数据
   *
   * @return
   */
  public List<MilitaryTermDataEntity> getTopData() {
    return militaryTermDataDao.findAllByParentId("0");
  }

  @Transactional
  public void saveAll(String data) {
    JsonObject jsonObject = JSONUtils.gson.toJsonTree(data).getAsJsonObject();
    Set<String> strings = jsonObject.keySet();
    final Integer[] i = {0};
    strings.forEach(s -> {
      MilitaryTermDataEntity entity = new MilitaryTermDataEntity();
      entity.setParentId("0");
      entity.setKey(s);
      entity.setSort(i[0]);
      i[0]++;
      MilitaryTermDataEntity save = militaryTermDataDao.save(entity);
      JsonArray jsonArray = jsonObject.getAsJsonArray(s);
      final Integer[] j = {0};
      jsonArray.forEach(o -> {
        Map<String, String> parse = JSONUtils.gson.fromJson(o.toString(), new TypeToken<>() {
        });
        String key = getKey(parse);
        MilitaryTermDataEntity entity1 = new MilitaryTermDataEntity();
        entity1.setParentId(save.getId());
        entity1.setKey(key);
        entity1.setValue(parse.get(key));
        entity1.setSort(j[0]);
        j[0]++;
        militaryTermDataDao.save(entity1);
      });
    });
  }

  private static String getKey(Map<String, String> map) {
    String obj = null;
    for (Map.Entry<String, String> entry : map.entrySet()) {
      obj = entry.getKey();
      if (obj != null) {
        break;
      }
    }
    return obj;
  }

  public List<Map<String, Object>> getAllByTree() {
    List<MilitaryTermDataEntity> topData = militaryTermDataDao.findAllByParentId("0");
    topData.sort(Comparator.comparingInt(MilitaryTermDataEntity::getSort));
    List<Map<String, Object>> mapList = new ArrayList<>(topData.size());
    topData.forEach(mjo -> {
      List<MilitaryTermDataEntity> allByParentId = militaryTermDataDao.findAllByParentId(mjo.getId());
      allByParentId.sort(Comparator.comparingInt(MilitaryTermDataEntity::getSort));
      List<Map<String, Object>> twoList = new ArrayList<>(allByParentId.size());
      allByParentId.forEach(m -> {
        Map<String, Object> map = new HashMap<>();
        map.put(m.getKey(), m.getValue());
        twoList.add(map);
      });
      Map<String, Object> map = new HashMap<>();
      map.put(mjo.getKey(), twoList);
      mapList.add(map);
    });
    return mapList;
  }

  /**
   * 新增类型
   *
   * @param: dto
   */
  @Transactional
  public MilitaryTermDataVO save(MilitaryTermDataDto dto) {
    MilitaryTermDataEntity entity = PojoUtils.convertOne(dto, MilitaryTermDataEntity.class);
    if (dto.getType().compareTo(0) == 0) {
      entity.setParentId("0");
    }
    MilitaryTermDataEntity byValue = militaryTermDataDao.findByValue(dto.getKey());
    if (ObjectUtil.isNotEmpty(byValue)) {
      throw new NIPException("内容重复，请查询输入");
    }
    //查询同类型下最大的位置
    Integer maxSort = militaryTermDataDao.findByParentIdMaxSort(entity.getParentId());
    entity.setSort(maxSort == null ? 1 : maxSort + 1);

    //保存到数据库
    MilitaryTermDataEntity save = militaryTermDataDao.save(entity);
    return PojoUtils.convertOne(save, MilitaryTermDataVO.class);
  }

  /**
   * 删除
   *
   * @param: vo
   */
  @Transactional(rollbackOn = Exception.class)
  public void delete(MilitaryTermDataVO vo) {
    MilitaryTermDataEntity entity = militaryTermDataDao.findById(vo.getId());
    //查看是由有子集
    List<MilitaryTermDataEntity> child = militaryTermDataDao.findAllByParentId(entity.getId());
    if (!child.isEmpty()) {
      throw new IllegalArgumentException("存在子级军语不能删除");
    }
    //修改位置
    militaryTermDataDao.sortSubtract(entity.getParentId(), entity.getSort());
    //删除
    militaryTermDataDao.deleteById(vo.getId());
  }

  /**
   * 查询所有
   */
  public List<MilitaryTermDataVO> findAll() {
    //先查询所有的父级
    List<MilitaryTermDataEntity> parentEntity = militaryTermDataDao.findAllByParentIdOrderBySort("0");
    return PojoUtils.convert(parentEntity, MilitaryTermDataVO.class, (e, v) -> {
      List<MilitaryTermDataEntity> child = militaryTermDataDao.findAllByParentIdOrderBySort(e.getId());
      v.setChild(PojoUtils.convert(child, MilitaryTermDataVO.class));
    });
  }

  /**
   * 修改
   *
   * @param: vo
   */
  @Transactional
  public MilitaryTermDataVO update(MilitaryTermDataVO vo) {
    MilitaryTermDataEntity militaryTermDataEntity = PojoUtils.convertOne(vo, MilitaryTermDataEntity.class);
    MilitaryTermDataEntity byValue = militaryTermDataDao.findByValue(vo.getValue());
    if (ObjectUtil.isNotEmpty(byValue) && !militaryTermDataEntity.getId().equals(byValue.getId())) {
      throw new NIPException("内容重复，请查询输入");
    }

    militaryTermDataDao.save(militaryTermDataEntity);
    return vo;
  }

  /**
   * 位置移动
   *
   * @param: dataMoveDto
   */
  @Transactional
  public void move(MilitaryTermDataMoveDto dataMoveDto) {
    MilitaryTermDataEntity source = militaryTermDataDao.findById(dataMoveDto.getSourceId());

    MilitaryTermDataEntity target = militaryTermDataDao.findById(dataMoveDto.getTargetId());
    //修改位置
    if (source.getSort().compareTo(target.getSort()) > 0) {
      //从下往上拖动
      militaryTermDataDao.downSwapUp(source.getSort(), target.getSort(), source.getParentId());
    } else {
      //从上往下拖动
      militaryTermDataDao.upSwapDown(source.getSort(), target.getSort(), source.getParentId());
    }
    //保存移动后的信息
    source.setSort(target.getSort());
    militaryTermDataDao.save(source);
  }

  public List<MilitaryTermDataVO> saveBatch(List<MilitaryTermDto> params) {
    excelHanle(params);
    return findAll();
  }


  @Transactional
  public void excelHanle(List<MilitaryTermDto> list) {
    for (MilitaryTermDto dto : list) {
      //第一个单元格是类型
      String parentName = dto.getParentName();
      //第二个单元格是子菜单
      String childName = dto.getChildName();
      //内容
      String content = dto.getContent();
      MilitaryTermDataEntity entity = militaryTermDataDao.findByParentIdAndKey("0", parentName);
      if (entity == null) {
        Integer maxSort = militaryTermDataDao.findByParentIdMaxSort("0");
        MilitaryTermDataEntity parent = new MilitaryTermDataEntity().setKey(parentName).setSort(maxSort + 1)
            .setParentId("0");
        MilitaryTermDataEntity save = militaryTermDataDao.save(parent);
        MilitaryTermDataEntity militaryTermDataEntity = new MilitaryTermDataEntity().setValue(content)
            .setParentId(save.getId())
            .setKey(childName).setSort(0);
        militaryTermDataDao.save(militaryTermDataEntity);
        return;
      }
      //查询子级
      MilitaryTermDataEntity childEntity = militaryTermDataDao.findByParentIdAndKey(entity.getId(), childName);
      if (childEntity == null) {
        Integer maxSort = militaryTermDataDao.findByParentIdMaxSort(entity.getId());
        childEntity = new MilitaryTermDataEntity().setParentId(entity.getId()).setSort(maxSort + 1).setValue(content)
            .setKey(childName);
      } else {
        childEntity.setValue(content);
      }
      militaryTermDataDao.save(childEntity);
    }
  }
}
