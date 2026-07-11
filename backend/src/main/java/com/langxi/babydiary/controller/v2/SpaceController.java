package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.SpaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v2/spaces")
public class SpaceController {
    private final SpaceService spaceService;
    private final CurrentUser currentUser;

    public SpaceController(SpaceService spaceService, CurrentUser currentUser) {
        this.spaceService = spaceService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Result<List<SpaceVO>> list() {
        return Result.success(spaceService.listSpaces(currentUser.getUserId()));
    }

    @PostMapping
    public Result<SpaceVO> create(@Valid @RequestBody CreateSpaceDTO dto) {
        return Result.success("空间已创建", spaceService.createSharedSpace(currentUser.getUserId(), dto));
    }

    @PutMapping("/{spaceId}")
    public Result<SpaceVO> rename(@PathVariable String spaceId, @Valid @RequestBody CreateSpaceDTO dto) {
        return Result.success("空间名称已更新", spaceService.rename(spaceId, currentUser.getUserId(), dto));
    }

    @GetMapping("/{spaceId}/members")
    public Result<List<SpaceMemberVO>> members(@PathVariable String spaceId) {
        return Result.success(spaceService.listMembers(spaceId, currentUser.getUserId()));
    }

    @GetMapping("/{spaceId}/tags")
    public Result<List<TagVO>> tags(@PathVariable String spaceId) {
        return Result.success(spaceService.listTags(spaceId, currentUser.getUserId()));
    }

    @PostMapping("/{spaceId}/tags")
    public Result<TagVO> createTag(@PathVariable String spaceId, @Valid @RequestBody TagCreateDTO dto) {
        return Result.success("标签已创建", spaceService.createTag(spaceId, currentUser.getUserId(), dto));
    }

    @PostMapping("/{spaceId}/invitations")
    public Result<SpaceInvitationVO> invite(@PathVariable String spaceId, @Valid @RequestBody SpaceInviteDTO dto) {
        return Result.success("邀请已创建", spaceService.invite(spaceId, currentUser.getUserId(), dto));
    }

    @PostMapping("/invitations/{token}/accept")
    public Result<SpaceVO> accept(@PathVariable String token) {
        return Result.success("已加入空间", spaceService.acceptInvitation(token, currentUser.getUserId()));
    }

    @PutMapping("/{spaceId}/members/{userId}/role")
    public Result<Void> role(@PathVariable String spaceId,
                             @PathVariable Integer userId,
                             @RequestParam @Pattern(regexp = "OWNER|MEMBER") String role) {
        spaceService.changeRole(spaceId, currentUser.getUserId(), userId, role);
        return Result.success("成员角色已更新", null);
    }

    @DeleteMapping("/{spaceId}/members/{userId}")
    public Result<Void> remove(@PathVariable String spaceId, @PathVariable Integer userId) {
        spaceService.removeMember(spaceId, currentUser.getUserId(), userId);
        return Result.success("成员已移除", null);
    }
}
