package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImportResultVO {
    private int importedDiaries;
    private int importedImages;
    private int importedMedia;
    private int skippedDiaries;
}
