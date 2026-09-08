package com.nip.common;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: wushilin 
 * @Data: 2022-04-07 13:49
 * @Description:
 */
@Data
//@ApiModel(value = "分页对象")
@NoArgsConstructor
@AllArgsConstructor
public class PageInfo<T> {

  //@ApiModelProperty(value = "总页数" ,position = 1)
  private int totalPage;

  //@ApiModelProperty(value = "总条数",position = 2)
  private long totalNumber;

  //@ApiModelProperty(value = "当前页",position = 3)
  private Integer currentPage;

  //@ApiModelProperty(value = "每页大小",position = 4)
  private Integer pageSize;

  //@ApiModelProperty(value = "数据",position = 5)
  private List<T> data;

}
