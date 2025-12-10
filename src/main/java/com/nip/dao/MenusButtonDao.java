package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.MenusButtonEntity;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class MenusButtonDao extends BaseRepository<MenusButtonEntity, String> {
  public List<MenusButtonEntity> findAllByMenusId(String menusId) {
    return find("menusId", menusId).list();
  }

  public List<MenusButtonEntity> findAllByMenusIdAndKey(String menusId, String key) {
    return find("menusId = :menusId and key = :key", Parameters.with("menusId", menusId).and("key", key)).list();
  }

  public void deleteAllByMenusId(String menusId) {
    delete("menusId", menusId);
  }
}
