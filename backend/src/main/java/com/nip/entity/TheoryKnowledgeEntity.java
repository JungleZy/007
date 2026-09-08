package com.nip.entity;

import com.nip.dto.sql.FindTheoryKnowledgeDto;
import com.nip.dto.sql.FindTheoryKnowledgeSwfTestDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TheoryKnowledgeEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-01-03 18:12:21
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(name = "find_theory_knowledge_dto_all_sql", query =
        "SELECT k.*,count(*) as swfs,u.user_name as createUserName ,c.`name` specialtyName,c1.`name` difficultyName "
            + "FROM t_theory_knowledge k INNER JOIN t_theory_knowledge_swf s ON s.knowledge_id = k.id LEFT JOIN t_user u ON u.id = k.create_user_id "
            + "LEFT JOIN t_theory_knowledge_classify c on  k.specialty_id =  c.id "
            + "LEFT JOIN t_theory_knowledge_classify c1 on  k.difficulty_id =  c1.id "
            + "WHERE k.type=:type and k.difficulty_id in (:dids) and k.specialty_id in (:sids)"
            + "GROUP BY k.id ORDER BY k.create_time desc",
        resultSetMapping = "theory_knowledge"),
    @NamedNativeQuery(name = "find_theory_knowledge_dto_all_sql_open", query =
        "SELECT k.*,count(*) as swfs,u.user_name as createUserName , c.`name` as specialtyName ,c1.`name` as difficultyName "
            + "FROM t_theory_knowledge k INNER JOIN t_theory_knowledge_swf s ON s.knowledge_id = k.id LEFT JOIN t_user u ON u.id = k.create_user_id "
            + "LEFT JOIN t_theory_knowledge_classify c on  k.specialty_id =  c.id "
            + "LEFT JOIN t_theory_knowledge_classify c1 on  k.difficulty_id =  c1.id "
            + "WHERE k.type=:type and k.status=:status and k.difficulty_id in (:dids) and k.specialty_id in (:sids)"
            + "GROUP BY k.id ORDER BY k.create_time desc",
        resultSetMapping = "theory_knowledge"),
    @NamedNativeQuery(name = "find_theory_knowledge_swf_test", query =
        "select k.id kId,k.credit,k.type ,ks.id ksId,ktu.user_id userId " +
            "from (select id,credit,type,FROM_UNIXTIME(create_time/1000,'%Y') `year` from t_theory_knowledge ) k " +
            "LEFT JOIN t_theory_knowledge_swf ks on k.id = ks.knowledge_id " +
            "LEFT JOIN t_theory_knowledge_test_user ktu on ks.id = ktu.knowledge_swf_id and ktu.user_id =:userId " +
            "where k.`year` = :year ",
        resultSetMapping = "theory_knowledge_swf_test"),
})
@SqlResultSetMappings({
    @SqlResultSetMapping(
        name = "theory_knowledge",
        classes = @ConstructorResult(
            targetClass = FindTheoryKnowledgeDto.class,
            columns = {
                @ColumnResult(name = "swfs"),
                @ColumnResult(name = "createUserName"),
                @ColumnResult(name = "specialtyName"),
                @ColumnResult(name = "difficultyName"),
                @ColumnResult(name = "title"),
                @ColumnResult(name = "cover"),
                @ColumnResult(name = "create_time"),
                @ColumnResult(name = "create_user_id"),
                @ColumnResult(name = "id"),
                @ColumnResult(name = "status"),
                @ColumnResult(name = "type")
            })
    ),
    @SqlResultSetMapping(
        name = "theory_knowledge_swf_test",
        classes = @ConstructorResult(
            targetClass = FindTheoryKnowledgeSwfTestDto.class,
            columns = {
                @ColumnResult(name = "kId"),
                @ColumnResult(name = "credit"),
                @ColumnResult(name = "type"),
                @ColumnResult(name = "ksId"),
                @ColumnResult(name = "userId")
            })
    )}
)
public class TheoryKnowledgeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  /**
   * 类型
   */
  private Integer type;
  /**
   * 开启关闭状态
   */
  private Integer status;
  private String cover = "/006/cover/base.jpg";
  /**
   * 标题
   */
  private String title;
  private String createUserId;
  private String createTime = String.valueOf(new Date().getTime());
  /**
   * 学分
   */
  private Double credit;

  /**
   * 难度id
   */
  private String difficultyId;

  /**
   * 专业id
   */
  private String specialtyId;

}
