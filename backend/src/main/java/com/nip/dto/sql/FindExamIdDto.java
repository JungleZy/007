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
public class FindExamIdDto {
  private String userName;
  private String user_img;
  private String wkno;
  private String id;
  private String user_id;
  private String exam_id;
  private String content;
  private Integer state;
  private Integer score;
  private String start_time;
  private String end_time;
  private Integer is_self_testing;
}
