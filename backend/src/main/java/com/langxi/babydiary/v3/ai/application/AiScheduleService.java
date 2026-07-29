package com.langxi.babydiary.v3.ai.application;

import com.langxi.babydiary.v3.ai.infrastructure.AiScheduleMapper;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
public class AiScheduleService {
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai"); private final SpaceAccess spaces;private final AiScheduleMapper mapper;
    public AiScheduleService(SpaceAccess spaces,AiScheduleMapper mapper){this.spaces=spaces;this.mapper=mapper;}
    public Schedule get(UUID spaceId,long accountId){SpaceAccess.SpaceContext space=spaces.requireMember(spaceId,accountId);return response(mapper.find(space.internalId()));}
    @Transactional public Schedule update(UUID spaceId,long accountId,boolean weekly,boolean monthly,boolean annual){
        SpaceAccess.SpaceContext space=spaces.requireMember(spaceId,accountId);if(!"OWNER".equals(space.role()))throw V3Exception.forbidden("SPACE_OWNER_REQUIRED","只有空间所有者可以修改自动回顾计划");
        LocalDateTime next=weekly||monthly||annual?LocalDateTime.ofInstant(ZonedDateTime.now(ZONE).plusDays(1).withHour(6).withMinute(5).withSecond(0).withNano(0).toInstant(),ZoneOffset.UTC):null;
        mapper.upsert(space.internalId(),accountId,weekly,monthly,annual,next);return response(mapper.find(space.internalId()));
    }
    private Schedule response(AiScheduleMapper.ScheduleRow row){return row==null?new Schedule(false,false,false,null):new Schedule(row.weeklyEnabled(),row.monthlyEnabled(),row.annualEnabled(),row.nextRunAt());}
    public record Schedule(boolean weeklyEnabled,boolean monthlyEnabled,boolean annualEnabled,LocalDateTime nextRunAt){}
}
