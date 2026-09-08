package com.nip.dto;

import com.nip.entity.TelegramTrainFloorContentEntity;
import com.nip.entity.TelegramTrainFloorEntity;
import lombok.Data;

import java.util.List;

/**
 * TelegramTrainFloorDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:53
 */
@Data
public class TelegramTrainFloorDto {
  private TelegramTrainFloorEntity floor;
  private List<TelegramTrainFloorContentEntity> floorContents;
}
