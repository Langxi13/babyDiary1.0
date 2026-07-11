package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiaryComment;
import com.langxi.babydiary.entity.DiaryReaction;
import com.langxi.babydiary.entity.DiaryRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface CollaborationMapper {
    void insertDiary(Diary diary);

    Diary findDiary(@Param("spaceId") Long spaceId, @Param("publicId") String publicId);

    Diary findDiaryByPublicId(@Param("publicId") String publicId);

    Diary findDiaryForUpdate(@Param("spaceId") Long spaceId, @Param("publicId") String publicId);

    Diary findDiaryByIdForShare(@Param("diaryId") Integer diaryId);

    List<Diary> findDiaryPage(@Param("spaceId") Long spaceId,
                              @Param("userId") Integer userId,
                              @Param("startDate") String startDate,
                              @Param("endDate") String endDate,
                              @Param("keyword") String keyword,
                              @Param("trash") boolean trash,
                              @Param("limit") int limit,
                              @Param("offset") long offset);

    int countDiaries(@Param("spaceId") Long spaceId,
                     @Param("userId") Integer userId,
                     @Param("startDate") String startDate,
                     @Param("endDate") String endDate,
                     @Param("keyword") String keyword,
                     @Param("trash") boolean trash);

    List<Diary> findExportDiaries(@Param("spaceId") Long spaceId, @Param("userId") Integer userId);

    int updateDiary(@Param("diary") Diary diary, @Param("expectedVersion") Integer expectedVersion);

    int softDelete(@Param("diaryId") Integer diaryId, @Param("expectedVersion") Integer expectedVersion);

    int restore(@Param("diaryId") Integer diaryId, @Param("expectedVersion") Integer expectedVersion);

    int restoreSnapshot(@Param("diary") Diary diary, @Param("expectedVersion") Integer expectedVersion);

    List<Diary> findExpiredTrash(@Param("cutoff") Timestamp cutoff, @Param("limit") int limit);

    void hardDelete(@Param("diaryId") Integer diaryId);

    void insertRevision(DiaryRevision revision);

    List<DiaryRevision> findRevisions(@Param("diaryId") Integer diaryId);

    DiaryRevision findRevision(@Param("diaryId") Integer diaryId, @Param("revisionId") Long revisionId);

    void insertComment(DiaryComment comment);

    List<DiaryComment> findComments(@Param("diaryId") Integer diaryId);

    DiaryComment findComment(@Param("diaryId") Integer diaryId, @Param("publicId") String publicId);

    int updateComment(@Param("commentId") Long commentId, @Param("userId") Integer userId, @Param("content") String content);

    int deleteComment(@Param("commentId") Long commentId, @Param("userId") Integer userId);

    int addReaction(@Param("diaryId") Integer diaryId, @Param("userId") Integer userId, @Param("emoji") String emoji);

    int removeReaction(@Param("diaryId") Integer diaryId, @Param("userId") Integer userId, @Param("emoji") String emoji);

    List<DiaryReaction> findReactions(@Param("diaryId") Integer diaryId);
}
