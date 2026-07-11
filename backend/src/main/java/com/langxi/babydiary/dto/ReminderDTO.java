package com.langxi.babydiary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ReminderDTO {
    private boolean enabled;

    @NotBlank(message = "提醒时间不能为空")
    @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d", message = "提醒时间格式应为HH:mm")
    private String time;

    @Min(value = 1, message = "星期范围应为1到7")
    @Max(value = 7, message = "星期范围应为1到7")
    private Integer dayOfWeek;
}
