package com.nip.common.repository;

import cn.hutool.core.lang.Assert;
import com.nip.common.specification.SpecificationExecutor;
import com.nip.common.utils.ToolUtil;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.transaction.Transactional;

import java.util.*;

@Transactional
public class BaseRepository<T, ID> extends SpecificationExecutor<T> implements PanacheRepositoryBase<T, ID> {

  @Transactional
  public <S extends T> S save(S entity) {
    Assert.notNull(entity, "Entity must not be null.");
    if (ToolUtil.isIdFieldEmpty(entity)) {
      entityManager.persist(entity);
      return entity;
    } else {
      return entityManager.merge(entity);
    }
  }

  @Transactional
  public <S extends T> S saveAndFlush(S entity) {
    S result = save(entity);
    flush();
    return result;
  }

  @Transactional
  public <S extends T> List<S> save(Iterable<S> entities) {
    Assert.notNull(entities, "Entities must not be null!");
    List<S> result = new ArrayList<>();
    for (S entity : entities) {
      result.add(save(entity));
    }
    return result;
  }

  @Transactional
  public <S extends T> List<S> saveAndFlush(Iterable<S> entities) {
    List<S> result = save(entities);
    flush();
    return result;
  }

  @Override
  @Transactional
  public void flush() {
    entityManager.flush();
  }
}
