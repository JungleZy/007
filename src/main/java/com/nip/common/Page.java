package com.nip.common;

import lombok.Data;

import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2023-07-28 16:13
 * @Description:
 */
@Data
public class Page<V> {

  private List<V> content;
  private Integer number;
  private Integer size;
  private Integer totalPages;
  private Integer totalElements;

}
