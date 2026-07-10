package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Anniversary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnniversaryMapper {
    List<Anniversary> findByUserId(@Param("userId") Integer userId);

    Anniversary findById(@Param("userId") Integer userId, @Param("anniversaryId") Integer anniversaryId);

    void insertAnniversary(Anniversary anniversary);

    void updateAnniversary(Anniversary anniversary);

    void deleteAnniversary(@Param("userId") Integer userId, @Param("anniversaryId") Integer anniversaryId);
}
