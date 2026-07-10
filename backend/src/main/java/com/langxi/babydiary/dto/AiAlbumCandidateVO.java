package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAlbumCandidateVO {
    @Pattern(regexp = "NEW|MERGE", message = "相册处理方式无效")
    private String mode;

    @Positive(message = "目标相册ID无效")
    private Integer targetAlbumId;

    @Size(max = 100, message = "目标相册名称过长")
    private String targetAlbumName;

    @Size(max = 100, message = "相册标题不能超过100个字符")
    private String title;

    @Size(max = 500, message = "相册说明不能超过500个字符")
    private String description;

    @Size(max = 500, message = "单个相册关联日记过多")
    private List<@NotNull @Positive Integer> diaryIds = new ArrayList<>();

    @Size(max = 500, message = "单个相册照片过多")
    private List<@NotNull @Positive Integer> imageIds = new ArrayList<>();

    private List<PhotoVO> photos = new ArrayList<>();
    private Boolean discarded = false;
}
