package com.nip.dto;

import com.nip.entity.TelegramTrainEntity;
import com.nip.entity.TelegramTrainUserEntity;
import lombok.Data;

import java.util.List;

/**
 * TelegramTrainDto
 *
 * @author < a href=" ">ZhangYang</ a>
 * @version v1.0.01
 * @date 2021-12-20 11:45
 */
@Data
public class TelegramTrainDto {
  private TelegramTrainEntity train;
  private TelegramTrainUserEntity trainUser;
  private List<TelegramTrainFloorDto> trainFloors;
}
