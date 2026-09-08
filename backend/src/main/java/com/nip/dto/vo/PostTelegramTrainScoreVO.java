package com.nip.dto.vo;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
//@ApiModel(value = "扣分详情")
@RegisterForReflection
public class PostTelegramTrainScoreVO {
    //少组
    int lack = 0;
    int correct = 0;
    int errorNumber = 0;

    //多组
    int moreGroup = 0;
    //少组
    int lackGroup = 0;

    // 串组
    int bunchGroup = 0;

    //多字或少字
    int moreOrLackWord = 0;

    //多行少行
    int moreOrLackLine = 0;

    /**
     * 怕发总数
     */
    int patTotalNum = 0;


    int dotScore = 0;
    int lineScore = 0;
    int codeScore = 0;
    int wordScore = 0 ;
    int groupScore = 0;
    int alterErrorScore = 0;

    int dotTotalTime = 0;
    int lineTotalTime = 0;
    int codeTotalTime = 0;
    int wordTotalTime = 0;
    int groupTotalTime = 0;
}
