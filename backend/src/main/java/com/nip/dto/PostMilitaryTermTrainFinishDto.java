package com.nip.dto;

import com.nip.entity.PostMilitaryTermTrainTestPaperEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: wushilin
 * @Data: 2022-06-27 11:45
 * @Description:
 */
@Schema(name = "军语密语完成训练DTO")
@Data
@RegisterForReflection
public class PostMilitaryTermTrainFinishDto implements Serializable {

  @Schema(name = "训练id")
  private String id;

  @Schema(name = "答案")
  private List<PostMilitaryTermTrainTestPaperEntity> testPaperList;

}
