package com.nip.common.utils;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

/**
 * 分页模型
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2018-09-27 15:31
 */
@Data
@RegisterForReflection
public class Page {
  /**
   * 当前页
   */
  private int page = 0;
  /**
   * 当前页条数
   */
  private int rows = 20;
  /**
   * 是否倒序，默认true
   */
  private Boolean desc = true;
  /**
   * 排序字段，默认id
   */
  private String sortBy = "id";

  /**
   * 当前页，最小 1（前端传 0 或负数时归一到首页）
   */
  public int getPage() {
    return Math.max(page, 1);
  }

  /**
   * 当前页条数，钳制到 [1, 200]，防止 rows=0 除零与超大分页拖垮数据库
   */
  public int getRows() {
    return Math.min(Math.max(rows, 1), 200);
  }
}
