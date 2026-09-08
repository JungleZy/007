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
public class FindTheoryKnowledgeDto {
  private Long swfs;
  private String createUserName;
  private String specialtyName;
  private String difficultyName;
  private String title;
  private String cover;
  private String createTime;
  private String createUserId;
  private String id;
  private int status;
  private int type;
}
