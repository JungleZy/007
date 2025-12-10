package com.nip.dto.general;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

import java.util.List;


@Data
@RegisterForReflection
public class GeneralKeyPatTrainDto {

    GeneralKeyPatSyncDto trainDto;

    List<GeneralKeyPatPageSyncDto> pageDto;

    List<GeneralKeyPatUserSyncDto> userDto;

    List<GeneralKeyPatUserValueSyncDto> userValueDto;

    List<GeneralKeyPatTrainMoreSyncDto> moreDto;

    List<UserSyncDto> users;
}
