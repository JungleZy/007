package com.nip.entity;

import com.nip.dto.sql.FindUserByRoleIdDto;
import com.nip.dto.sql.FindUserByStatusDescDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_user")
@Cacheable(value = false)
@NamedNativeQueries({
    @NamedNativeQuery(
        name = "find_user_by_status",
        query = "SELECT u.id,u.id_card idCard,u.phone,u.`status`,u.user_account userAccount,u.user_img userImg,u.user_name userName,u.user_sex userSex,r.title "
            + "FROM t_user u LEFT JOIN t_user_role ur on ur.user_id = u.id LEFT JOIN t_role r ON r.id = ur.role_id ORDER BY u.`status` DESC",
        resultSetMapping = "user_by_status"),
    @NamedNativeQuery(
        name = "find_user_by_role_id",
        query = "SELECT u.id,u.user_name userName,u.user_account userAccount FROM t_user u LEFT JOIN t_user_role r on u.id = r.user_id where r.role_id = :id",
        resultSetMapping = "user_by_role_id")
})
@SqlResultSetMappings({
    @SqlResultSetMapping(name = "user_by_status", classes = @ConstructorResult(targetClass = FindUserByStatusDescDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "idCard"), @ColumnResult(name = "phone"),
        @ColumnResult(name = "status"), @ColumnResult(name = "userAccount"), @ColumnResult(name = "userImg"),
        @ColumnResult(name = "userName"), @ColumnResult(name = "userSex"), @ColumnResult(name = "title"),
    })),
    @SqlResultSetMapping(name = "user_by_role_id", classes = @ConstructorResult(targetClass = FindUserByRoleIdDto.class, columns = {
        @ColumnResult(name = "id"), @ColumnResult(name = "userName"), @ColumnResult(name = "userAccount")
    }))
})
@Schema(name = "UserEntity", title = "用户对象")
public class UserEntity implements Comparable<UserEntity> {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Schema(title = "编号", required = true, type = SchemaType.STRING)
  private String id;
  @Schema(title = "工号", required = true, type = SchemaType.STRING)
  private String wkno;
  @Schema(title = "手机号码", required = true, type = SchemaType.STRING)
  private String phone;
  @Schema(title = "电子邮箱", required = true, type = SchemaType.STRING)
  private String email;
  @Schema(title = "用户名", required = true, type = SchemaType.STRING)
  private String userAccount;
  @Schema(title = "用户头像", type = SchemaType.STRING)
  private String userImg;
  @Schema(title = "用户姓名", type = SchemaType.STRING)
  private String userName;
  @Schema(title = "身份证", required = true, type = SchemaType.STRING)
  private String idCard;
  @Schema(title = "用户性别", type = SchemaType.STRING)
  private Integer userSex;
  @Schema(title = "用户密码", type = SchemaType.STRING)
  private String password;
  @Schema(title = "token", required = true, type = SchemaType.STRING)
  private String token;
  @Schema(title = "设备编号", type = SchemaType.STRING)
  private String deviceId;
  @Schema(title = "登录时间", required = true, type = SchemaType.STRING)
  private String updateFlag;
  @Schema(title = "账号状态：0、正常，1、审核中，-1、停用中", type = SchemaType.INTEGER)
  private Integer status;
  @Schema(title = "生日", type = SchemaType.STRING)
  private String bday;
  @Schema(title = "入职时间", type = SchemaType.STRING)
  private String eday;
  @Schema(title = "离职时间", type = SchemaType.STRING)
  private String dday;

  @Override
  public int compareTo(UserEntity o) {
    return this.userName.compareTo(o.userName);
  }

}
