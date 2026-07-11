package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.dto.ReminderDTO;
import com.langxi.babydiary.dto.ReminderVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.Reminder;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.mapper.ReminderMapper;
import com.langxi.babydiary.mapper.SpaceMapper;
import com.langxi.babydiary.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {
    @Mock private ReminderMapper mapper;
    @Mock private SpaceService spaceService;
    @Mock private SpaceMapper spaceMapper;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ReminderService service;

    @Test
    void dailyReminderUsesTheUsersTimezoneAndStoresANextRun() {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        space.setPublicId("space-one");
        User user = new User();
        user.setTimezone("Asia/Shanghai");
        Reminder stored = new Reminder();
        stored.setPublicId("reminder-one");
        stored.setType("DAILY");
        stored.setEnabled(true);
        stored.setScheduleJson("{\"time\":\"20:30\",\"dayOfWeek\":null}");
        when(spaceService.requireSpace("space-one")).thenReturn(space);
        when(userMapper.findById(2)).thenReturn(user);
        when(mapper.list(2, 7L)).thenReturn(Collections.singletonList(stored));
        ReminderDTO dto = new ReminderDTO();
        dto.setEnabled(true);
        dto.setTime("20:30");

        ReminderVO result = service.save("space-one", 2, "daily", dto);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(mapper).upsert(captor.capture());
        assertThat(captor.getValue().getNextRunAt()).isNotNull();
        assertThat(result.getTime()).isEqualTo("20:30");
    }
}
