package com.nip.ws.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RoomModel
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2022-05-25 9:33
 */
@Data
@RegisterForReflection
public class RoomModel {
  private String id;
  private String name;
  private Integer type = 0; // 0，一发多收；1，多发多收；2，抄收竞速；3，发报竞速
  private Integer status = 0; // 0,静默阶段；1，检测阶段；2，进行阶段
  private String password;
  private Integer nnt = 8;
  private String admin;
  /**
   * 房间成员。跨连接线程并发增删与遍历，必须保持 CopyOnWriteArrayList
   */
  private List<UserModel> users = new CopyOnWriteArrayList<>();
}
