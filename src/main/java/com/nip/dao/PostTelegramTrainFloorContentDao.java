package com.nip.dao;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.PostTelegramTrainFloorContentEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;

import static com.nip.common.constants.BaseConstants.TRAIN_ID;

@ApplicationScoped
public class PostTelegramTrainFloorContentDao
    extends BaseRepository<PostTelegramTrainFloorContentEntity, String> {

  @Transactional
  public void updateById(String id, String value, String time, String keys) {
    update("moresValue = ?2,moresTime = ?3 ,patKeys = ?4 where id = ?1", id, value, time, keys);
  }

  @Transactional
  public void clearByTranId(String tranId) {
    update("moresValue=\"[]\",moresTime=\"[]\",patKeys=\"[]\"  where trainId = ?1", tranId);
  }

  public List<PostTelegramTrainFloorContentEntity> findByFloorNumberAndTrainIdOrderBySort(Integer floor, String id) {
    return find("floorNumber = ?1 and trainId =?2 order by sort", floor, id).list();
  }

  public List<PostTelegramTrainFloorContentEntity> findByTrainIdOrderByFloorNumberSort(String tranId) {
    return find("trainId = ?1 order by floorNumber, sort", tranId).list();
  }

  public PostTelegramTrainFloorContentEntity findByTrainId(String tranId) {
    return find("trainId = ?1 order by floorNumber desc", tranId).firstResult();
  }

  public List<Integer> findByTrainIdCountFloor(String trainId) {
    return entityManager.createQuery(
        "select floorNumber from t_post_telegram_train_floor_content where trainId =:trainId group by floorNumber",
        Integer.class
    ).setParameter(TRAIN_ID, trainId).getResultList();
  }

  public PostTelegramTrainFloorContentEntity findByTrainIdOrderByFloorNumberDescSortDesc(String trainId) {
    return find("trainId = ?1 order by floorNumber desc,sort desc", trainId).firstResult();
  }

  public List<PostTelegramTrainFloorContentEntity> findAllByTrainIdOrderByFloorNumberAscSortAscLimit(String id, Integer number, Integer pageSize) {
    TypedQuery<PostTelegramTrainFloorContentEntity> query = entityManager.createQuery(
        "SELECT p FROM t_post_telegram_train_floor_content p WHERE p.trainId = :trainId " +
            "ORDER BY p.floorNumber ASC, p.sort ASC",
        PostTelegramTrainFloorContentEntity.class
    );
    query.setParameter(TRAIN_ID, id);
    query.setFirstResult(number);
    query.setMaxResults(pageSize);
    return query.getResultList();
  }

  public Integer findCountByTrainIdOrderByFloorNumberAscSortAsc(String id) {
    List<Long> resultList = entityManager.createQuery("select count(id) " +
            "from t_post_telegram_train_floor_content " +
            "where trainId=?1 order by floorNumber , sort ", Long.class)
        .setParameter(1, id).getResultList();
    if (resultList == null || resultList.isEmpty()) {
      return 0;
    } else {
      Long firstElement = resultList.getFirst();
      return firstElement != null ? firstElement.intValue() : 0;
    }
  }
}
