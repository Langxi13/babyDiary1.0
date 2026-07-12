package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.SystemInvitationConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemInvitationConfigMapper {
    SystemInvitationConfig findConfig();

    SystemInvitationConfig findConfigForShare();

    int insertIfAbsent(SystemInvitationConfig config);

    void upsertConfig(SystemInvitationConfig config);
}
