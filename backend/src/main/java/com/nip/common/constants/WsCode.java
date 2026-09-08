package com.nip.common.constants;

/**
 * CodeConstants
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-24 14:33
 */
public enum WsCode {
  ON_LINE(0, "上线"),
  CLOSE(1, "关闭连接"),
  USER_LIST(10, "用户列表"),
  WEAPON_LIST(11, "群组列表"),
  FLOOR_CONTENT_DATA(1000, "@/data/floor/content"),
  FLOOR_CONTENT_DATA_OVER(1001, "@/data/floor/content/over"),
  TM(100, "用户聊天消息"),
  TEACHERCHANGEEXAMSTATE(18001, "监考员修改考核状态"),
  STUDENTCHANGEEXAMSTATE(18002, "学员修改考核状态"),
  USERUPLOADCONTENT(18003, "学员修改考核状态");

  private int code;
  private String content;

  WsCode(int code, String content) {
    this.code = code;
    this.content = content;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
