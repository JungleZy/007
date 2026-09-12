package com.nip.dao.general.telex;

import com.nip.common.repository.BaseRepository;
import com.nip.entity.simulation.telex.GeneralTelexPatUserValueEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * 组训电传的学员页行。表里有两类行，靠 {@code sort} 区分，结算只重建后者：
 * <ul>
 *   <li><b>原始提交行</b>（{@code sort = -1}）：学员提交的整页文本 + 采集轮次 + 采集区间 + 收到时刻，
 *       一经写入只能由同页的"继续采集"扩展，<b>结算不删不改</b>；</li>
 *   <li><b>分析行</b>（{@code sort >= 0}）：结算时由 {@code TelexPatUtils.handle} 逐组比对产出，可反复重建。</li>
 * </ul>
 */
@ApplicationScoped
public class GeneralTelexPatUserValueDao extends BaseRepository<GeneralTelexPatUserValueEntity, String> {
  public List<GeneralTelexPatUserValueEntity> findByTrainIdAndUserIdOrderByPageNumberAscSortAsc(String trainId, String userId) {
    return find("trainId = ?1 and userId = ?2 order by pageNumber asc, sort asc", trainId, userId).list();
  }
  public List<GeneralTelexPatUserValueEntity> findTwoPage(String id, String userId) {
    return find("trainId = ?1 and userId = ?2 and (pageNumber = 1 or pageNumber = 2) order by pageNumber, sort", id, userId).list();
  }
  public List<GeneralTelexPatUserValueEntity> findByTrainIdAndPageNumberAndUserIdOrderBySort(String trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3 order by sort asc", trainId, pageNumber, userId).list();
  }

  /** 某页的原始提交行；正常只有一行，返回列表是为了让重复行在结算前被发现而不是被静默取首。 */
  public List<GeneralTelexPatUserValueEntity> findRawByTrainIdAndPageNumberAndUserId(String trainId, Integer pageNumber, String userId) {
    return find("trainId = ?1 and pageNumber = ?2 and userId = ?3 and sort = -1", trainId, pageNumber, userId).list();
  }

  /** 该学员本训练的全部原始提交行，按页码升序。 */
  public List<GeneralTelexPatUserValueEntity> findRawByTrainIdAndUserId(String trainId, String userId) {
    return find("trainId = ?1 and userId = ?2 and sort = -1 order by pageNumber asc", trainId, userId).list();
  }

  @Transactional
  public void deleteRawByTrainIdAndPageNumberAndUserId(String trainId, Integer pageNumber, String userId) {
    delete("trainId = ?1 and pageNumber = ?2 and userId = ?3 and sort = -1", trainId, pageNumber, userId);
  }

  public List<GeneralTelexPatUserValueEntity> findByPageNumberAndTrainIdAndUserId(Integer pageNumber, String trainId, String userId) {
    return find(" trainId = ?1 and pageNumber = ?2 and userId = ?3", trainId, pageNumber, userId).list();
  }

  /**
   * 只删分析行。结算会重复执行（重试、收尾扫描），删全部会连同原始采集时间轴一起抹掉，
   * 之后任何重算都只能命中"已保存页缺少原始采集时间轴"。
   */
  @Transactional
  public void deleteAnalysisByTrainIdAndUserId(String trainId, String userId) {
    delete("trainId = ?1 and userId = ?2 and sort > -1", trainId, userId);
    flush();
  }
}
