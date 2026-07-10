package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Anniversary;
import lombok.Data;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Data
public class AnniversaryVO {
    private Integer anniversaryId;
    private String title;
    private String date;
    private String description;
    private String coverImagePath;
    private Integer sort;
    private Long daysPassed;
    private Long daysUntil;

    public static AnniversaryVO fromEntity(Anniversary anniversary) {
        AnniversaryVO vo = new AnniversaryVO();
        vo.setAnniversaryId(anniversary.getAnniversaryId());
        vo.setTitle(anniversary.getTitle());
        Date date = anniversary.getDate();
        if (date != null) {
            LocalDate anniversaryDate = date.toLocalDate();
            LocalDate today = LocalDate.now();
            vo.setDate(anniversaryDate.toString());
            vo.setDaysPassed(ChronoUnit.DAYS.between(anniversaryDate, today));
            vo.setDaysUntil(ChronoUnit.DAYS.between(today, anniversaryDate));
        }
        vo.setDescription(anniversary.getDescription());
        vo.setCoverImagePath(anniversary.getCoverImagePath());
        vo.setSort(anniversary.getSort());
        return vo;
    }
}
