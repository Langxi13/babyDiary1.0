package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.AiAlbumProposal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAlbumProposalMapper {
    void insert(AiAlbumProposal proposal);
    AiAlbumProposal findById(@Param("userId") Integer userId, @Param("proposalId") Integer proposalId);
    void updateContent(AiAlbumProposal proposal);
    void updateStatus(@Param("userId") Integer userId, @Param("proposalId") Integer proposalId, @Param("status") String status);
    void delete(@Param("userId") Integer userId, @Param("proposalId") Integer proposalId);
}
