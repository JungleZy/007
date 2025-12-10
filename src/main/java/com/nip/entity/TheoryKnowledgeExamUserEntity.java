package com.nip.entity;

import com.nip.dto.AllExamDto;
import com.nip.dto.sql.FindExamDto;
import com.nip.dto.sql.FindExamIdDto;
import com.nip.dto.sql.FindUserMonthAvgScoreDto;
import com.nip.dto.vo.TheoryKnowledgeExamUserSelfVO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/2/22 15:38
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_theory_knowledge_exam_user") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({
        @NamedNativeQuery(name = "find_all_exam_id_sql", query =
                "SELECT examUser.*, u.user_account userName,u.user_img,u.wkno " +
                        "FROM t_theory_knowledge_exam_user examUser LEFT JOIN t_user u on examUser.user_id = u.id " +
                        "WHERE examUser.exam_id = :examId",
                resultSetMapping = "exam_id"),
        @NamedNativeQuery(name = "find_all_exam_user", query =
                "SELECT exam.*, examUser.state userState, u.user_name teacherName" +
                        " FROM t_theory_knowledge_exam_user examUser " +
                        "LEFT JOIN t_theory_knowledge_exam exam on exam.id = examUser.exam_id " +
                        "LEFT JOIN t_user u on exam.teacher = u.id  " +
                        "WHERE exam.state = :s1 AND examUser.user_id = :u or exam.state = :s2 " +
                        " AND examUser.user_id = :u and examUser.is_self_testing=1 order by exam.start_time desc",
                resultSetMapping = "knowledge_exam"),
        @NamedNativeQuery(name = "find_all_exam_user_two", query =
                "SELECT exam.*, examUser.state userState, u.user_name teacherName" +
                        " FROM t_theory_knowledge_exam_user examUser " +
                        "LEFT JOIN t_theory_knowledge_exam exam on exam.id = examUser.exam_id " +
                        "LEFT JOIN t_user u on exam.teacher = u.id  " +
                        "WHERE exam.state =:s" +
                        " AND examUser.user_id = :u and examUser.is_self_testing=1 order by  exam.start_time desc",
                resultSetMapping = "knowledge_exam"),
        @NamedNativeQuery(name = "find_user_month_avg_score", query =
                "select t.m, ROUND(avg(t.score),2) avg "
                        + "from ("
                        + "select score,DATE_FORMAT(end_time,'%c') m "
                        + "from t_theory_knowledge_exam_user "
                        + "where user_id = :ui and state =4 and start_time is not null and end_time is not null and end_time like :et"
                        + ") t GROUP BY t.m",
                resultSetMapping = "user_month_avg_score"),

        @NamedNativeQuery(name = "find_all_is_self_testing", query =
                "select e.title, eu.* from t_theory_knowledge_exam_user  eu "
                        + " LEFT JOIN t_theory_knowledge_exam e on eu.exam_id =e.id"
                        + " where eu.user_id =:userId and eu.is_self_testing=0 order by eu.start_time desc",
                resultSetMapping = "is_self_testing"),

        @NamedNativeQuery(name = "find_all_exam_one", query =
                "SELECT exam.*,examUser.state userState,u.user_name teacherName " +
                        " FROM t_theory_knowledge_exam_user examUser" +
                        " LEFT JOIN t_theory_knowledge_exam exam ON exam.id = examUser.exam_id" +
                        " LEFT JOIN t_user u ON exam.teacher = u.id " +
                        " WHERE" +
                        " exam.state =:state1 AND examUser.user_id =:userId OR exam.state =:state2 AND examUser.user_id =:userId AND examUser.is_self_testing = 1 " +
                        " ORDER BY exam.start_time DESC",
                resultSetMapping = "all_exam_dto"),

        @NamedNativeQuery(name = "find_all_exam_two", query =
                "SELECT exam.*, examUser.state userState, u.user_name teacherName " +
                        " FROM t_theory_knowledge_exam_user examUser " +
                        "LEFT JOIN t_theory_knowledge_exam exam on exam.id = examUser.exam_id " +
                        "LEFT JOIN t_user u on exam.teacher = u.id  " +
                        "WHERE exam.state =:state1 " +
                        " AND examUser.user_id =:userId and examUser.is_self_testing=1 order by  exam.start_time desc",
                resultSetMapping = "all_exam_dto"),
})
@SqlResultSetMappings({
        @SqlResultSetMapping(
                name = "exam_id",
                classes = @ConstructorResult(
                        targetClass = FindExamIdDto.class,
                        columns = {
                                @ColumnResult(name = "userName"),
                                @ColumnResult(name = "user_img"),
                                @ColumnResult(name = "wkno"),
                                @ColumnResult(name = "id"),
                                @ColumnResult(name = "user_id"),
                                @ColumnResult(name = "exam_id"),
                                @ColumnResult(name = "content"),
                                @ColumnResult(name = "state"),
                                @ColumnResult(name = "score"),
                                @ColumnResult(name = "start_time"),
                                @ColumnResult(name = "end_time"),
                                @ColumnResult(name = "is_self_testing")
                        })
        ),
        @SqlResultSetMapping(
                name = "knowledge_exam",
                classes = @ConstructorResult(
                        targetClass = FindExamDto.class,
                        columns = {
                                @ColumnResult(name = "userState"),
                                @ColumnResult(name = "teacherName"),
                                @ColumnResult(name = "id"),
                                @ColumnResult(name = "title"),
                                @ColumnResult(name = "start_time", type = String.class),
                                @ColumnResult(name = "end_time"),
                                @ColumnResult(name = "duration"),
                                @ColumnResult(name = "create_user_id"),
                                @ColumnResult(name = "create_time"),
                                @ColumnResult(name = "teacher"),
                                @ColumnResult(name = "state")
                        })
        ),
        @SqlResultSetMapping(
                name = "user_month_avg_score",
                classes = @ConstructorResult(
                        targetClass = FindUserMonthAvgScoreDto.class,
                        columns = {
                                @ColumnResult(name = "m"),
                                @ColumnResult(name = "avg")
                        })
        ),
        @SqlResultSetMapping(
                name = "is_self_testing",
                classes = @ConstructorResult(
                        targetClass = TheoryKnowledgeExamUserSelfVO.class,
                        columns = {
                                @ColumnResult(name = "title"),
                                @ColumnResult(name = "start_time"),
                                @ColumnResult(name = "score"),
                                @ColumnResult(name = "content"),
                                @ColumnResult(name = "exam_id")
                        })
        ),
        @SqlResultSetMapping(
                name = "all_exam_dto",
                classes = @ConstructorResult(
                        targetClass = AllExamDto.class,
                        columns = {
                                @ColumnResult(name = "userState"),
                                @ColumnResult(name = "teacherName"),
                                @ColumnResult(name = "id"),
                                @ColumnResult(name = "title"),
                                @ColumnResult(name = "start_time", type = String.class),
                                @ColumnResult(name = "end_time"),
                                @ColumnResult(name = "duration"),
                                @ColumnResult(name = "create_user_id"),
                                @ColumnResult(name = "create_time"),
                                @ColumnResult(name = "teacher"),
                                @ColumnResult(name = "state")
                        })
        ),
})
public class TheoryKnowledgeExamUserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String userId;
    private String examId;
    /**
     * 题目与答案
     */
    private String content;
    /**
     * 状态(1.学生未准备2.学生考核中3.学生提交考试4.老师已阅卷)
     */
    private Integer state;
    /**
     * 得分
     */
    private Integer score;
    /**
     * 开始时间
     */
    private String startTime;
    /**
     * 结束时间
     */
    private String endTime;

    /**
     * 0 是自测 1 不是自测
     */
    private Integer isSelfTesting;
}
