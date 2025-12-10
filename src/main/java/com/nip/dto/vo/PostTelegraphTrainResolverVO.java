package com.nip.dto.vo;

import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema
public class PostTelegraphTrainResolverVO {


    @Schema(title = "多组")
    private List<PostTelegraphKeyPatResolverDetailVO> moreGroups = new ArrayList<>();

    @Schema(title = "多行")
    private List<PostTelegraphKeyPatResolverDetailVO> moreLine = new ArrayList<>();


}
