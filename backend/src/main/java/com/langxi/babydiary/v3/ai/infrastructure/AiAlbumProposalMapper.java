package com.langxi.babydiary.v3.ai.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiAlbumProposalMapper {
    @Insert("""
            INSERT INTO ai_album_proposal(public_id,space_id,created_by,status,start_date,end_date,prompt,model,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{createdBy},'PENDING',#{startDate},#{endDate},#{prompt},#{model},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "proposalId")
    void insertProposal(ProposalInsert row);

    @Select("""
            SELECT p.proposal_id,p.public_id,s.public_id AS space_public_id,p.status,p.start_date,p.end_date,
                   p.prompt,p.model,p.created_at,p.updated_at
            FROM ai_album_proposal p JOIN diary_space s ON s.space_id=p.space_id
            WHERE p.space_id=#{spaceId} AND p.created_by=#{accountId} AND p.public_id=#{publicId}
            """)
    ProposalRow findProposal(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                             @Param("publicId") byte[] publicId);

    @Update("UPDATE ai_album_proposal SET status=#{status},updated_at=UTC_TIMESTAMP(6) " +
            "WHERE proposal_id=#{proposalId} AND status='PENDING'")
    int updateStatus(@Param("proposalId") long proposalId, @Param("status") String status);

    @Insert("""
            INSERT INTO ai_album_candidate(space_id,proposal_id,mode,target_album_id,title,description,start_date,end_date,discarded,position)
            VALUES(#{spaceId},#{proposalId},#{mode},#{targetAlbumId},#{title},#{description},#{startDate},#{endDate},#{discarded},#{position})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "candidateId")
    void insertCandidate(CandidateInsert row);

    @Select("""
            SELECT c.candidate_id,c.mode,c.title,c.description,c.start_date,c.end_date,c.discarded,c.position,
                   a.public_id AS target_album_public_id,a.name AS target_album_name
            FROM ai_album_candidate c LEFT JOIN album a ON a.album_id=c.target_album_id AND a.deleted_at IS NULL
            WHERE c.proposal_id=#{proposalId} ORDER BY c.position,c.candidate_id
            """)
    List<CandidateRow> findCandidates(long proposalId);

    @Delete("DELETE FROM ai_album_candidate WHERE proposal_id=#{proposalId}")
    void deleteCandidates(long proposalId);

    @Insert("INSERT INTO ai_album_candidate_diary(space_id,candidate_id,diary_id,position) VALUES(#{spaceId},#{candidateId},#{diaryId},#{position})")
    void insertCandidateDiary(@Param("spaceId") long spaceId, @Param("candidateId") long candidateId,
                              @Param("diaryId") long diaryId, @Param("position") int position);

    @Insert("INSERT INTO ai_album_candidate_media(space_id,candidate_id,asset_id,position) VALUES(#{spaceId},#{candidateId},#{assetId},#{position})")
    void insertCandidateMedia(@Param("spaceId") long spaceId, @Param("candidateId") long candidateId,
                              @Param("assetId") long assetId, @Param("position") int position);

    @Select("""
            SELECT d.public_id FROM ai_album_candidate_diary cd JOIN diary d ON d.diary_id=cd.diary_id
            WHERE cd.candidate_id=#{candidateId} ORDER BY cd.position,cd.diary_id
            """)
    List<byte[]> findCandidateDiaries(long candidateId);

    @Select("""
            SELECT a.public_id FROM ai_album_candidate_media cm JOIN media_asset a ON a.asset_id=cm.asset_id
            WHERE cm.candidate_id=#{candidateId} AND a.deleted_at IS NULL AND a.status='READY'
            ORDER BY cm.position,cm.asset_id
            """)
    List<byte[]> findCandidateMedia(long candidateId);

    @Select("""
            SELECT d.diary_id,d.public_id AS diary_public_id,a.asset_id,a.public_id AS asset_public_id
            FROM diary d JOIN diary_media dm ON dm.diary_id=d.diary_id
            JOIN media_asset a ON a.asset_id=dm.asset_id
            WHERE d.space_id=#{spaceId} AND d.deleted_at IS NULL AND d.diary_date BETWEEN #{startDate} AND #{endDate}
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
              AND a.media_type='IMAGE' AND a.deleted_at IS NULL AND a.status='READY'
            ORDER BY d.diary_date,d.diary_id,dm.position,a.asset_id
            """)
    List<DiaryMediaRow> findDiaryMedia(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                                       @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("""
            <script>
            SELECT diary_id AS internal_id,public_id FROM diary WHERE space_id=#{spaceId} AND public_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<IdRow> resolveDiaries(@Param("spaceId") long spaceId, @Param("ids") List<byte[]> ids);

    @Select("""
            <script>
            SELECT asset_id AS internal_id,public_id FROM media_asset WHERE space_id=#{spaceId} AND deleted_at IS NULL
              AND status='READY' AND media_type='IMAGE' AND public_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<IdRow> resolveMedia(@Param("spaceId") long spaceId, @Param("ids") List<byte[]> ids);

    final class ProposalInsert {
        private Long proposalId; private final byte[] publicId; private final long spaceId; private final long createdBy;
        private final LocalDate startDate; private final LocalDate endDate; private final String prompt; private final String model;
        public ProposalInsert(byte[] publicId, long spaceId, long createdBy, LocalDate startDate, LocalDate endDate, String prompt, String model) {
            this.publicId=publicId; this.spaceId=spaceId; this.createdBy=createdBy; this.startDate=startDate; this.endDate=endDate; this.prompt=prompt; this.model=model;
        }
        public Long getProposalId(){return proposalId;} public void setProposalId(Long v){proposalId=v;}
        public byte[] getPublicId(){return publicId;} public long getSpaceId(){return spaceId;} public long getCreatedBy(){return createdBy;}
        public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;} public String getPrompt(){return prompt;} public String getModel(){return model;}
    }
    final class CandidateInsert {
        private Long candidateId; private final long spaceId; private final long proposalId; private final String mode; private final Long targetAlbumId;
        private final String title; private final String description; private final LocalDate startDate; private final LocalDate endDate; private final boolean discarded; private final int position;
        public CandidateInsert(long spaceId,long proposalId,String mode,Long targetAlbumId,String title,String description,LocalDate startDate,LocalDate endDate,boolean discarded,int position){
            this.spaceId=spaceId;this.proposalId=proposalId;this.mode=mode;this.targetAlbumId=targetAlbumId;this.title=title;this.description=description;this.startDate=startDate;this.endDate=endDate;this.discarded=discarded;this.position=position;}
        public Long getCandidateId(){return candidateId;} public void setCandidateId(Long v){candidateId=v;} public long getSpaceId(){return spaceId;} public long getProposalId(){return proposalId;} public String getMode(){return mode;} public Long getTargetAlbumId(){return targetAlbumId;} public String getTitle(){return title;} public String getDescription(){return description;} public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;} public boolean isDiscarded(){return discarded;} public int getPosition(){return position;}
    }
    final class ProposalRow {
        private long proposalId; private byte[] publicId; private byte[] spacePublicId; private String status; private LocalDate startDate; private LocalDate endDate; private String prompt; private String model; private LocalDateTime createdAt; private LocalDateTime updatedAt;
        public long getProposalId(){return proposalId;} public byte[] getPublicId(){return publicId;} public byte[] getSpacePublicId(){return spacePublicId;} public String getStatus(){return status;} public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;} public String getPrompt(){return prompt;} public String getModel(){return model;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
        public void setProposalId(long v){proposalId=v;} public void setPublicId(byte[] v){publicId=v;} public void setSpacePublicId(byte[] v){spacePublicId=v;} public void setStatus(String v){status=v;} public void setStartDate(LocalDate v){startDate=v;} public void setEndDate(LocalDate v){endDate=v;} public void setPrompt(String v){prompt=v;} public void setModel(String v){model=v;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
    }
    final class CandidateRow {
        private long candidateId; private String mode; private String title; private String description; private LocalDate startDate; private LocalDate endDate; private boolean discarded; private int position; private byte[] targetAlbumPublicId; private String targetAlbumName;
        public long getCandidateId(){return candidateId;} public String getMode(){return mode;} public String getTitle(){return title;} public String getDescription(){return description;} public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;} public boolean isDiscarded(){return discarded;} public int getPosition(){return position;} public byte[] getTargetAlbumPublicId(){return targetAlbumPublicId;} public String getTargetAlbumName(){return targetAlbumName;}
        public void setCandidateId(long v){candidateId=v;} public void setMode(String v){mode=v;} public void setTitle(String v){title=v;} public void setDescription(String v){description=v;} public void setStartDate(LocalDate v){startDate=v;} public void setEndDate(LocalDate v){endDate=v;} public void setDiscarded(boolean v){discarded=v;} public void setPosition(int v){position=v;} public void setTargetAlbumPublicId(byte[] v){targetAlbumPublicId=v;} public void setTargetAlbumName(String v){targetAlbumName=v;}
    }
    record DiaryMediaRow(long diaryId, byte[] diaryPublicId, long assetId, byte[] assetPublicId){}
    record IdRow(long internalId, byte[] publicId){}
}
