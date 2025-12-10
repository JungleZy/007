package com.nip.common.constants;

/**
 * CodeConstants
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-05-24 14:33
 */
public enum UnionConstants {
  GET_UNION_INFO(0, "获取联合训练相关信息"),
  GET_ROOM_INFO(1, "获取房间相关信息"),
  USER_JOIN(2, "用户加入"),
  USER_EXIT(3, "用户退出"),
  USER_LIST(10, "用户列表"),
  ROOM_LIST(11, "房间列表"),
  ROOM_USER_BROADCAST(111, "房间用户更新通知"),
  ROOM_STATUS_CHANGE(112, "改变房间状态"),
  ADD_ROOM(12, "添加房间"),
  ADD_ROOM_SUCCESS(120, "添加房间成功"),
  ADD_ROOM_FAIL(121, "添加房间失败"),
  UPDATE_ROOM_INFO(121, "更新房间信息"),
  JOIN_ROOM(13, "进入房间"),
  JOIN_ROOM_SUCCESS(130, "进入房间成功"),
  JOIN_ROOM_FAIL(131, "进入房间失败"),
  EXIT_ROOM(14, "退出房间"),
  EXIT_ROOM_SUCCESS(140, "退出房间成功"),
  EXIT_ROOM_FAIL(141, "退出房间失败"),
  REMOVE_ROOM(15, "解散房间"),
  REMOVE_ROOM_SUCCESS(150, "解散房间成功"),
  REMOVE_ROOM_FAIL(151, "解散房间失败"),
  REMOVE_ROOM_BROADCAST(152, "解散房间广播"),
  UPDATE_ROOM(16, "更新房间"),
  UPDATE_ROOM_USER(17,"更新房间用户信息"),
  UPDATE_ROOM_USER_BROADCAST(171,"更新房间用户信息广播"),
  SEAT_INSPECT(18,"发起席位状态检测"),
  SEAT_INSPECT_ACCEPT(181,"席位状态接收"),
  SEAT_INSPECT_REPLY(182,"席位状态回执"),
  SEAT_INSPECT_BROADCAST(183,"席位状态回执广播"),
  ROOM_MESSAGE(20, "房间消息"),
  UNKNOWN(-1, "未知指令");

  private int code;
  private String content;

  UnionConstants(int code, String content) {
    this.code = code;
    this.content = content;
  }

  public static UnionConstants getByCode(int code) {
    for (UnionConstants unionConstants : UnionConstants.values()) {
      if (unionConstants.code == code) {
        return unionConstants;
      }
    }
    return UNKNOWN;
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
