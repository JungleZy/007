package com.nip.entity;

import com.nip.dto.sql.TheoryKnowledgeTestUserCountSwfNumDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * TheoryKnowledgeTestUserEntity
 *
 * @author  < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date    2022-01-03 14:29:22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_test_user") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries(
        @NamedNativeQuery(
                name = "count_swf_num",
                query = "select t.time,count(t.time) count from (select FROM_UNIXTIME(create_time/1000,'%Y-%m') time  from t_theory_knowledge_test_user where user_id  =?1 ) t where t.time like ?2  GROUP BY t.time",
                resultSetMapping = "cont_swf_num_mapping"
        )
)
@SqlResultSetMappings(
        @SqlResultSetMapping(
                name = "cont_swf_num_mapping",
                classes = @ConstructorResult(
                        targetClass = TheoryKnowledgeTestUserCountSwfNumDto.class, columns = {
                        @ColumnResult(name = "time",type = String.class),
                        @ColumnResult(name = "count",type = Integer.class)
                })
        )
)
public class TheoryKnowledgeTestUserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String knowledgeId;
  private String knowledgeSwfId;
  private String userId;
  /**
   * 题目与答案，这个数据通过t_theory_knowladge_test_content形成json格式保存在这里
   */
  private String content;
  /**
   * 得分
   */
  private Integer score;
  /**
   * 创建时间
   */
  private String createTime = new Date().getTime() + "";
}
