package com.nip.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nip.common.utils.StrictIntegerDeserializer;
import com.nip.dto.vo.PostTelegraphKeyPatTrainPageMessageVO;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@RegisterForReflection
public class PostTelegraphKeyPatTrainPageDto {
  private String id;
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer pageNumber;
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer protocolVersion;
  @JsonDeserialize(using = StrictIntegerDeserializer.class)
  private Integer attempt;
  private List<PostTelegraphKeyPatTrainPageMessageVO> value;
  private List<CaptureInterval> captureIntervals;
}
