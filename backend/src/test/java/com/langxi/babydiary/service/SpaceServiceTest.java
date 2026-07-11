package com.langxi.babydiary.service;

import com.langxi.babydiary.dto.CreateSpaceDTO;
import com.langxi.babydiary.dto.SpaceVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.SpaceMember;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.SpaceMapper;
import com.langxi.babydiary.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceServiceTest {
    @Mock private SpaceMapper spaceMapper;
    @Mock private UserMapper userMapper;
    @Mock private TagService tagService;

    @Test
    void sharedSpaceInitializesStorageUsageAndReturnsPersistedTimestamps() {
        SpaceService service = new SpaceService(spaceMapper, userMapper, tagService);
        doAnswer(invocation -> {
            invocation.<DiarySpace>getArgument(0).setSpaceId(7L);
            return null;
        }).when(spaceMapper).insertSpace(any(DiarySpace.class));

        DiarySpace stored = new DiarySpace();
        stored.setSpaceId(7L);
        stored.setPublicId("space-one");
        stored.setName("家庭空间");
        stored.setType("SHARED");
        stored.setCreatedAt(Timestamp.from(Instant.parse("2026-07-11T00:00:00Z")));
        when(spaceMapper.findById(7L)).thenReturn(stored);

        CreateSpaceDTO dto = new CreateSpaceDTO();
        dto.setName(" 家庭空间 ");
        SpaceVO result = service.createSharedSpace(1, dto);

        verify(spaceMapper).ensureStorageUsage(7L);
        assertThat(result.getCreatedAt()).isEqualTo(stored.getCreatedAt());
        assertThat(result.getName()).isEqualTo("家庭空间");
    }

    @Test
    void removingTheLastOwnerLocksTheSpaceBeforeCountingOwners() {
        SpaceService service = new SpaceService(spaceMapper, userMapper, tagService);
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        space.setPublicId("space-one");
        SpaceMember owner = new SpaceMember();
        owner.setUserId(1);
        owner.setRole("OWNER");
        when(spaceMapper.findByPublicId("space-one")).thenReturn(space);
        when(spaceMapper.findMember(7L, 1)).thenReturn(owner);
        when(spaceMapper.countOwners(7L)).thenReturn(1);

        assertThatThrownBy(() -> service.removeMember("space-one", 1, 1))
                .isInstanceOf(BusinessException.class);

        verify(spaceMapper).lockSpace(7L);
        verify(spaceMapper).countOwners(7L);
    }
}
