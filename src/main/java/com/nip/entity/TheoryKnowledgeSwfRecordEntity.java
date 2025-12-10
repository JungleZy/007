package com.nip.entity;

import com.nip.dto.sql.TheoryKnowledgeSwfRecordContStudyTimeDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TheoryKnowledgeSwfRecordEntity
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-03 18:12:05
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_swf_record") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries(@NamedNativeQuery(
        name = "count_study_time",
        query = "select t.date as date,Round(sum(TIMESTAMPDIFF(SECOND,t.join_time,t.exit_time))/3600,2)  as time " +
                "from " +
                "(select *,DATE_FORMAT(join_time,'%Y-%m') date from t_theory_knowledge_swf_record " +
                "where " +
                "user_id = ?1 and join_time like ?2) t group by t.date",
        resultSetMapping = "count_study_time_mapping"
))
@SqlResultSetMappings(
        @SqlResultSetMapping(
                name = "count_study_time_mapping",
                classes = @ConstructorResult(
                        targetClass = TheoryKnowledgeSwfRecordContStudyTimeDto.class,
                        columns = {
                                @ColumnResult(name = "date"),
                                @ColumnResult(name = "time")
                        }
                )
        )
)
public class TheoryKnowledgeSwfRecordEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String knowledgeId;
  private String knowledgeSwfId;
  private String userId;
  private String joinTime;
  private String exitTime;
  private int type;
}
