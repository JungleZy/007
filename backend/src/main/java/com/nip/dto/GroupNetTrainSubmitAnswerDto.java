package com.nip.dto;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;


/**
 * @Author: wushilin
 * @Data: 2023-08-22 11:36
 * @Description:
 */
@Data
@Schema(title = "提交答案")
@RegisterForReflection
public class GroupNetTrainSubmitAnswerDto {
  @Schema(title = "id")
  private Integer id;

  @Schema(title = "答案")
  private String answer;

  @JsonIgnore
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private boolean unsupportedFields;

  @JsonAnySetter
  public void unsupportedField(String name, Object value) {
    unsupportedFields = true;
  }

  public void validateFields() {
    if (unsupportedFields) throw new IllegalArgumentException("提交只接受训练id和原始答案，不接受客户端成绩等字段");
  }

}
