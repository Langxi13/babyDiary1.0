package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.AiReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiReportMapper {
    void insert(AiReport report);

    AiReport findById(@Param("userId") Integer userId, @Param("reportId") Integer reportId);

    List<AiReport> findPage(@Param("userId") Integer userId, @Param("type") String type, @Param("limit") int limit, @Param("offset") long offset);

    int count(@Param("userId") Integer userId, @Param("type") String type);

    void delete(@Param("userId") Integer userId, @Param("reportId") Integer reportId);

    List<AiReport> findSpacePage(@Param("spaceId") Long spaceId, @Param("type") String type,
                                 @Param("limit") int limit, @Param("offset") long offset);

    int countSpace(@Param("spaceId") Long spaceId, @Param("type") String type);

    AiReport findSpaceReport(@Param("spaceId") Long spaceId, @Param("reportId") Integer reportId);

    int existsSpacePeriod(@Param("spaceId") Long spaceId, @Param("type") String type, @Param("period") String period);
}
