package com.nip.service;

import com.nip.common.exception.ForbiddenException;
import com.nip.dao.RoleDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 训练/房间写操作的授权口径，全仓唯一一处。拒绝一律抛 {@link ForbiddenException} → HTTP 200 + {@code code:207}。
 *
 * <p>这里只有两个口径，且**互不重叠**，不要再新增第三个：
 * <ul>
 *   <li>{@link #requireTrainOwner}：<b>个人训练域</b>（个人手键/电子键/电传），只判创建者。
 *       个人训练没有「房间内组训人」这个概念，也不存在「管理员代练」的业务场景，
 *       放开管理员等于凭空引进一条「管理员可改他人个人训练」的新能力，所以这一档到创建者为止。</li>
 *   <li>{@link #requireWritableTrain}：<b>组训 / 仿真房间域</b>，口径 = 创建者 ∪ 该训练（房间）内组训人 ∪ 系统管理员。
 *       组训场景里带训的组训人常常不是建训人，收窄到「仅创建者」会让他开始不了自己带的训练。</li>
 * </ul>
 *
 * <p>属主字段名各域不同（{@code createUser} / {@code createUserId} / {@code userId}），
 * 「组训人」的判定表也各不相同，因此两者都由各域<b>显式传入</b>，这里不做任何反射或字段名猜测。
 */
@ApplicationScoped
public class TrainWriteAccess {
  @Inject RoleDao roleDao;

  /**
   * 个人训练域：只有创建者可写。
   *
   * @param actorId 调用者用户 id（token 已解析成功，身份成立）
   * @param ownerId 训练的创建者 id，由各域按自己的属主字段取
   * @param subject 拒绝日志/异常里标识目标的短语，例如 {@code "个人手键训练 " + id}
   */
  public void requireTrainOwner(String actorId, String ownerId, String subject) {
    if (actorId == null || !Objects.equals(ownerId, actorId)) {
      throw new ForbiddenException("非创建者操作" + subject);
    }
  }

  /**
   * 组训 / 仿真房间域：创建者 ∪ 组训人 ∪ 管理员可写。
   *
   * @param organizer 「调用者是该训练/房间内的组训人」的判定；调用者是创建者时<b>不会</b>求值，
   *                  以免白白打一次成员表查询。没有组训人概念的房间传返回 {@code false} 的判定即可。
   */
  public void requireWritableTrain(String actorId, String ownerId, BooleanSupplier organizer, String subject) {
    if (!manages(actorId, ownerId, organizer)) {
      throw new ForbiddenException("非授权者操作" + subject);
    }
  }

  /**
   * 组训口径的谓词形态。批量场景（如离线导出逐条跳过无权的行）需要布尔值而不是异常，用这个。
   */
  public boolean manages(String actorId, String ownerId, BooleanSupplier organizer) {
    if (actorId == null) {
      return false;
    }
    return Objects.equals(ownerId, actorId)
        || organizer.getAsBoolean()
        || roleDao.existsAdminRoleByUserId(actorId);
  }
}
