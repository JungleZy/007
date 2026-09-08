package com.nip.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收报训练基础设置
 *
 * @version v1.0.01
 * @Author：BBB
 * @Date:Create 2022/4/6 13:40
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "t_post_ticker_tape_train_setting") //对应的数据库表
@Cacheable(value = false)
public class PostTickerTapeTrainSettingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;
  private String type;
  private Integer rate;
  private String text;
  //点标准时长
  private Integer dot;
  private LocalDateTime createTime = LocalDateTime.now();

}
