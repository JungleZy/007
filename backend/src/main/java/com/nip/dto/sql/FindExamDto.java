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
public class FindExamDto {
  private Integer userState;
  private String teacherName;
  private String id;
  private String title;
  private String startTime;
  private String endTime;
  private String duration;
  private String createUserId;
  private String createTime;
  private String teacher;
  private Integer state;
}
