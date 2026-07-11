package com.langxi.babydiary.mapper;

import com.langxi.babydiary.dto.SearchResultVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchMapper {
    void upsertDiary(@Param("diaryId") Integer diaryId);

    void deleteDiaryById(@Param("diaryId") Integer diaryId);

    void deleteDiary(@Param("publicId") String publicId);

    int refreshDiaryDocuments();

    int removeStaleDiaryDocuments();

    List<SearchResultVO> searchFullText(@Param("spaceId") Long spaceId,
                                        @Param("userId") Integer userId,
                                        @Param("query") String query,
                                        @Param("limit") int limit);

    List<SearchResultVO> searchLike(@Param("spaceId") Long spaceId,
                                    @Param("userId") Integer userId,
                                    @Param("query") String query,
                                    @Param("limit") int limit);
}
