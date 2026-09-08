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
@RegisterForReflection
public class FindAllExamDto {
  private String dis;
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
