package com.nip.dto.vo.param;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * @Author: wushilin
 * @Data: 2022-04-12 11:06
 * @Description:
 */
@Data
@Schema(title = "汉字录入更新状态对象")
public class EnteringExerciseUpdateParam {

  private String id;

}
