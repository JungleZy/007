package com.nip.common.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.function.Supplier;

/**
 * 唯一键表「幂等懒建」的支撑件：读路径首次访问时补建唯一行，与并发方撞唯一键时收敛到同一行。
 *
 * <p>为什么懒建的读-改-写必须整体放进一个<b>独立</b>事务，而不是在调用方事务里插入后重读：
 * <ol>
 *   <li>撞唯一键的一方会把当前事务标记成 rollback-only，同一事务里无法继续重读；</li>
 *   <li>MySQL 默认 REPEATABLE READ，同一事务的一致性读快照看不到别的事务在快照之后提交的行，
 *       即便事务还能用，重读也仍然是 null；</li>
 *   <li>已经从调用方事务读出的行如果在别的事务里被插入，回到调用方事务 {@code merge} 会因为
 *       快照里查不到该行而退化成 INSERT，再次撞唯一键。</li>
 * </ol>
 * 所以正确的收敛姿势是：在独立事务里「查不到就插」，撞唯一键时只回滚这个独立事务，
 * 再换<b>一个新的</b>独立事务原样重跑一次 —— 新事务的新快照能看到对方刚提交的那行，于是走更新分支。
 *
 * <p>本类必须是独立 bean：{@code REQUIRES_NEW} 依赖 CDI 拦截器，同类内部自调用不经过拦截器，
 * {@link #inNewTransaction(Supplier)} 只有通过注入的 bean 引用调用才真正开新事务，
 * 因此重试逻辑留在调用方，不在本类里自调用。
 */
@ApplicationScoped
public class IdempotentWrite {

  /** 在一个独立事务里执行 {@code action}：调用方事务被挂起，action 的失败只回滚它自己。 */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public <R> R inNewTransaction(Supplier<R> action) {
    return action.get();
  }

  /**
   * 异常链里是否有完整性约束冲突（MySQL 唯一键冲突 1062 / SQLState 23000 会以
   * {@link SQLIntegrityConstraintViolationException} 出现在链上，Hibernate 侧则包成
   * {@link org.hibernate.exception.ConstraintViolationException}）。
   * 不是约束冲突的异常必须原样抛出，禁止吞掉。
   */
  public static boolean isConstraintConflict(Throwable e) {
    Throwable current = e;
    while (current != null) {
      if (current instanceof SQLIntegrityConstraintViolationException
          || current instanceof org.hibernate.exception.ConstraintViolationException) {
        return true;
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return false;
  }
}
