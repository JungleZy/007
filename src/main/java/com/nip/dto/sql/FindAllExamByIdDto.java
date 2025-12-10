package com.nip.dto.sql;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UserInfoAllByStatusDescDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2023-07-19 11:33
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
// 只要不是new的对象都需要显式注册一下，quarkus在构建本机可执行文件时，GraalVM 以封闭世界假设运行。它分析调用树并删除所有未直接使用的类/方法/字段
@RegisterForReflection
public class FindAllExamByIdDto {
  private String userName;
  private String id;
  private String title;
  private String start_time;
  private String end_time;
  private String duration;
  private String create_user_id;
  private String create_time;
  private String teacher;
  private Integer state;
}
