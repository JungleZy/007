package com.nip.entity;

import com.nip.dto.sql.FindMenusByRoleIdDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MenusEntity
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-08-02 15:34
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_menus") //对应的数据库表
@Cacheable(value = false)
@NamedNativeQueries({@NamedNativeQuery(name = "find_menus_by_role_id", query =
  "SELECT m.id id,m.parent_id parentId,m.key,m.path,m.name,m.icon,m.icon_f iconF,m.title,m.is_menu isMenu,m.is_bread isBread,m.component,m.sort,m.height,m.keep_alive keepAlive,rm.per"
    + " FROM t_menus as m LEFT JOIN t_role_menus as rm ON rm.menu_id = m.id LEFT JOIN t_role as r on r.id = rm.role_id"
    + " WHERE r.id=:rid  ORDER BY m.sort", resultSetMapping = "menus_by_role_id"),})
@SqlResultSetMappings({
  @SqlResultSetMapping(name = "menus_by_role_id", classes = @ConstructorResult(targetClass = FindMenusByRoleIdDto.class, columns = {
    @ColumnResult(name = "id"), @ColumnResult(name = "parentId"), @ColumnResult(name = "key"),
    @ColumnResult(name = "path"), @ColumnResult(name = "name"), @ColumnResult(name = "icon"),
    @ColumnResult(name = "iconF"), @ColumnResult(name = "height"), @ColumnResult(name = "title"),
    @ColumnResult(name = "isMenu"), @ColumnResult(name = "isBread"), @ColumnResult(name = "component"),
    @ColumnResult(name = "sort"), @ColumnResult(name = "per"),})),})
public class MenusEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String parentId;
  @Column(name = "`key`")
  private String key;
  private String path;
  private String name;
  private String icon;
  private String iconF;
  private String height;
  private String title;
  private Integer isMenu = 0;
  private Integer isBread = 0;
  private String component;
  private Integer sort;
  private Integer keepAlive = 0;
}
