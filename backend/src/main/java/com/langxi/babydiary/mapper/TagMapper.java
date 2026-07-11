package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Tag;
import com.langxi.babydiary.dto.DiaryTagRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper {
    List<Tag> findTagsByUserId(@Param("userId") Integer userId);

    List<Tag> findTagsByDiaryId(@Param("diaryId") Integer diaryId);

    List<DiaryTagRow> findTagsByDiaryIds(@Param("diaryIds") List<Integer> diaryIds);

    Tag findTagByName(@Param("userId") Integer userId, @Param("name") String name);

    Tag findTagById(@Param("userId") Integer userId, @Param("tagId") Integer tagId);

    List<Tag> findTagsBySpaceId(@Param("spaceId") Long spaceId);

    Tag findTagBySpaceAndName(@Param("spaceId") Long spaceId, @Param("name") String name);

    Tag findTagBySpaceAndId(@Param("spaceId") Long spaceId, @Param("tagId") Integer tagId);

    void insertTag(Tag tag);

    void deleteDiaryTagsByDiaryId(@Param("diaryId") Integer diaryId);

    void insertDiaryTags(@Param("diaryId") Integer diaryId, @Param("tagIds") List<Integer> tagIds);
}
