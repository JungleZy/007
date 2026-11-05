package com.nip.dto.vo;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@RegisterForReflection
//@ApiModel
public class PostTelegramTrainResolverVO {
    // null 表示历史结算未保存定位依据；空列表表示该页没有逐组扣分。
    private List<HandKeyDeductionEvidence> deductionEvidence;
    private List<HandKeyTimingEvidence> timingEvidence;

    //@ApiModelProperty(value = "解析后的正确报文")
    private List<String> resolverMessage;

    //@ApiModelProperty(value = "多组")
    private List<PostTelegramTrainResolverDetailVO> moreGroups;

    //@ApiModelProperty(value = "多行")
    private List<PostTelegramTrainResolverDetailVO> moreLine = new ArrayList<>();


    //@ApiModelProperty(value = "拍发日志")
    private List<String> resolverPatLogs;

    //@ApiModelProperty(value = "拍发表示 0点 1划")
    private List<String> resolverMoresValue;

    //@ApiModelProperty(value = "拍发电划耗时")
    private List<String> resolverMoresTime;

}
