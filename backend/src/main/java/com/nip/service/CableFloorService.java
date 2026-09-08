package com.nip.service;

import com.google.gson.reflect.TypeToken;
import com.nip.common.utils.JSONUtils;
import com.nip.dao.CableFloorDao;
import com.nip.entity.CableFloorEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class CableFloorService {
  private final CableFloorDao cableFloorDao;

  @Inject
  public CableFloorService(CableFloorDao cableFloorDao) {
    this.cableFloorDao = cableFloorDao;
  }

  public List<List<List<String>>> findCableFloor(String cableId, Integer floorNumber, Integer startPage) {
    List<List<List<String>>> floorString = new ArrayList<>();
    if (floorNumber != null) {
      List<CableFloorEntity> floors = cableFloorDao.findAllByCableIdAndFloorNumber(cableId, floorNumber);
      List<List<String>> f = new ArrayList<>();
      for (CableFloorEntity floor : floors) {
        List<String> o = JSONUtils.fromJson(floor.getMoresKey(), new TypeToken<>() {
        });
        f.add(o);
      }
      floorString.add(f);
      return floorString;
    } else {
      List<CableFloorEntity> floors = cableFloorDao.findAllByCableId(cableId);
      List<Integer> flag = new ArrayList<>();
      for (CableFloorEntity floor : floors) {
        if (!flag.contains(floor.getFloorNumber())) {
          flag.add(floor.getFloorNumber());
          floorString.add(new ArrayList<>());
        }

        List<String> o = JSONUtils.fromJson(floor.getMoresKey(), new TypeToken<>() {
        });
        floorString.get(floor.getFloorNumber()).add(o);
      }
      if (null != startPage && startPage - 1 > 0 && floorString.size() > startPage - 1) {
          // 移除从索引0到lengthToRemove-1的元素
          floorString.subList(0, startPage - 1).clear();
        }

      return floorString;
    }
  }
}
