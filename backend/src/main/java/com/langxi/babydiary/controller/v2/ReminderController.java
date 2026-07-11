package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.ReminderDTO;
import com.langxi.babydiary.dto.ReminderVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/reminders")
public class ReminderController {
    private final ReminderService reminderService;
    private final CurrentUser currentUser;

    public ReminderController(ReminderService reminderService, CurrentUser currentUser) {
        this.reminderService = reminderService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Result<List<ReminderVO>> list(@PathVariable String spaceId) {
        return Result.success(reminderService.list(spaceId, currentUser.getUserId()));
    }

    @PutMapping("/{type}")
    public Result<ReminderVO> save(@PathVariable String spaceId,
                                   @PathVariable String type,
                                   @Valid @RequestBody ReminderDTO dto) {
        return Result.success("提醒设置已保存", reminderService.save(spaceId, currentUser.getUserId(), type, dto));
    }
}
