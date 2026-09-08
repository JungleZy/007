package com.nip.entity;

import com.nip.dto.sql.FindAllExamByIdDto;
import com.nip.dto.sql.FindAllExamDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 15:32
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_exam") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
  @NamedNativeQuery(name = "find_all_exam", query =
    "SELECT DISTINCT(exam.id) dis,exam.*, u.user_name userName FROM t_theory_knowledge_exam exam " +
      "LEFT JOIN t_user u ON exam.teacher = u.id " +
      "LEFT JOIN t_theory_knowledge_exam_user eu on exam.id = eu.exam_id " +
      "WHERE (exam.state = :s1 OR exam.state = :s2) and eu.is_self_testing = 1",
    resultSetMapping = "all_exam"),
  @NamedNativeQuery(name = "find_all_exam_by_id", query =
    "SELECT exam.* ,u.user_name userName " +
      "FROM t_theory_knowledge_exam exam  LEFT JOIN t_user u on exam.teacher = u.id " +
      "where exam.id=:id",
    resultSetMapping = "all_exam_by_id"),
})
@SqlResultSetMappings({
  @SqlResultSetMapping(
    name = "all_exam",
    classes = @ConstructorResult(
      targetClass = FindAllExamDto.class,
      columns = {
        @ColumnResult(name = "dis"),
        @ColumnResult(name = "userName"),
        @ColumnResult(name = "id"),
        @ColumnResult(name = "title"),
        @ColumnResult(name = "start_time"),
        @ColumnResult(name = "end_time"),
        @ColumnResult(name = "duration"),
        @ColumnResult(name = "create_user_id"),
        @ColumnResult(name = "create_time"),
        @ColumnResult(name = "teacher"),
        @ColumnResult(name = "state")
      })
  ),
  @SqlResultSetMapping(
  name = "all_exam_by_id",
  classes = @ConstructorResult(
    targetClass = FindAllExamByIdDto.class,
    columns = {
      @ColumnResult(name = "userName"),
      @ColumnResult(name = "id"),
      @ColumnResult(name = "title"),
      @ColumnResult(name = "start_time"),
      @ColumnResult(name = "end_time"),
      @ColumnResult(name = "duration"),
      @ColumnResult(name = "create_user_id"),
      @ColumnResult(name = "create_time"),
      @ColumnResult(name = "teacher"),
      @ColumnResult(name = "state")
    })
)
})
public class TheoryKnowledgeExamEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 考核名称
   */
  private String title;
  private String startTime;
  private String endTime;
  /**
   * 时长
   */
  private String duration;
  /**
   * 创建人
   */
  private String createUserId;
  /**
   * 创建时间
   */
  private String createTime = new Date().getTime() + "";
  /**
   * 监考人
   */
  private String teacher; // 当前正在编辑的Id
  /**
   * 状态（1:未开始，2：已开始，3：已结束，4：已阅卷）
   */
  private Integer state;
}
