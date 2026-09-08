package com.nip.dto.general;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.Serializable;

@Data
@RegisterForReflection
public class GeneralKeyPatTrainMoreSyncDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 训练id
     */
    @Schema(title = "训练id")
    @Column(name = "train_id")
    private Integer trainId;

    /**
     * 用户id
     */
    @Column(name = "user_id")
    @Schema(title = "用户id")
    private String userId;

    /**
     * 页码
     */
    @Schema(title = "页码")
    @Column(name = "page_number")
    private Integer pageNumber;

    /**
     * 多组
     */
    @Schema(title = "多组")
    @Column(name = "more_group")
    private String moreGroup;

    /**
     * 多行
     */
    @Schema(title = "多行")
    @Column(name = "more_line")
    private String moreLine;

}
