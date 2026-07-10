package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.AiAlbumProposal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAlbumProposalVO {
    private Integer proposalId;
    private String status;
    private String startDate;
    private String endDate;
    private String prompt;
    private String model;
    @NotNull(message = "相册推荐不能为空")
    @Size(max = 50, message = "单次最多处理50个相册")
    private List<@Valid AiAlbumCandidateVO> albums = new ArrayList<>();

    public static AiAlbumProposalVO fromEntity(AiAlbumProposal proposal, List<AiAlbumCandidateVO> albums) {
        AiAlbumProposalVO vo = new AiAlbumProposalVO();
        vo.setProposalId(proposal.getProposalId());
        vo.setStatus(proposal.getStatus());
        vo.setStartDate(proposal.getStartDate() == null ? null : proposal.getStartDate().toString());
        vo.setEndDate(proposal.getEndDate() == null ? null : proposal.getEndDate().toString());
        vo.setPrompt(proposal.getPrompt());
        vo.setModel(proposal.getModel());
        vo.setAlbums(albums);
        return vo;
    }
}
