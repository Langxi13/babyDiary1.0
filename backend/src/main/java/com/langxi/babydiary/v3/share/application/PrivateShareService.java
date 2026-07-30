package com.langxi.babydiary.v3.share.application;

import com.langxi.babydiary.v3.identity.application.StepUpService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.share.infrastructure.PrivateShareMapper;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class PrivateShareService {
    private final SpaceAccess spaces;private final PrivateShareMapper mapper;private final StepUpService stepUp;
    private final PasswordEncoder passwords;private final MediaUrlSigner mediaUrls;private final SecureRandom random=new SecureRandom();
    public PrivateShareService(SpaceAccess spaces,PrivateShareMapper mapper,StepUpService stepUp,PasswordEncoder passwords,MediaUrlSigner mediaUrls){
        this.spaces=spaces;this.mapper=mapper;this.stepUp=stepUp;this.passwords=passwords;this.mediaUrls=mediaUrls;}

    @Transactional
    public Created create(UUID spaceId,UUID diaryId,V3Principal principal,String stepUpToken,int hours,String password,Integer maxViews){
        SpaceAccess.SpaceContext space=spaces.requireMember(spaceId,principal.accountId());
        PrivateShareMapper.DiaryRow diary=manageable(space,diaryId,principal.accountId());if(diary.isLocked())stepUp.require(principal,stepUpToken);
        if(hours<1||hours>720)throw V3Exception.badRequest("SHARE_EXPIRY_INVALID","分享有效期应为1小时到30天");
        if(maxViews!=null&&(maxViews<1||maxViews>10000))throw V3Exception.badRequest("SHARE_VIEWS_INVALID","浏览次数应为1到10000");
        String normalized=password==null||password.isBlank()?null:password;
        if(normalized!=null&&(normalized.length()<4||normalized.length()>64))throw V3Exception.badRequest("SHARE_PASSWORD_INVALID","分享密码长度应为4到64位");
        byte[] tokenBytes=new byte[32];random.nextBytes(tokenBytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);UUID id=UUID.randomUUID();
        LocalDateTime expires=LocalDateTime.now(ZoneOffset.UTC).plusHours(hours);
        mapper.insert(new PrivateShareMapper.ShareInsert(BinaryUuid.toBytes(id),sha256(token),space.internalId(),diary.getDiaryId(),principal.accountId(),
                normalized==null?null:passwords.encode(normalized),expires,maxViews));
        return new Created(id,"/shared/"+token,expires,maxViews);
    }
    public List<Summary> list(UUID spaceId,UUID diaryId,V3Principal principal,String stepUpToken){
        SpaceAccess.SpaceContext space=spaces.requireMember(spaceId,principal.accountId());PrivateShareMapper.DiaryRow diary=manageable(space,diaryId,principal.accountId());
        if(diary.isLocked())stepUp.require(principal,stepUpToken);return mapper.findActive(diary.getDiaryId(),principal.accountId()).stream().map(this::summary).toList();
    }
    @Transactional public void revoke(UUID shareId,long accountId){if(mapper.revoke(BinaryUuid.toBytes(shareId),accountId)!=1)throw V3Exception.notFound("SHARE_NOT_FOUND","分享不存在或无权撤销");}
    @Transactional
    public SharedDiary open(String token,String password){
        if(token==null||token.isBlank())throw V3Exception.notFound("SHARE_NOT_FOUND","分享不存在或已过期");
        PrivateShareMapper.OpenRow row=mapper.findForOpen(sha256(token));LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        if(row==null||row.getExpiresAt().isBefore(now)||(row.getMaxViews()!=null&&row.getViewCount()>=row.getMaxViews()))throw V3Exception.notFound("SHARE_NOT_FOUND","分享不存在或已过期");
        if(row.getPasswordHash()!=null&&(password==null||!passwords.matches(password,row.getPasswordHash())))throw new V3Exception(HttpStatus.UNAUTHORIZED,"SHARE_PASSWORD_INVALID","分享密码不正确");
        if(mapper.incrementView(row.getShareId(),now)!=1)throw V3Exception.notFound("SHARE_NOT_FOUND","分享不存在或已过期");
        UUID spaceId=BinaryUuid.fromBytes(row.getSpacePublicId());List<SharedMedia> media=mapper.findMedia(row.getDiaryId()).stream().map(item->{UUID id=BinaryUuid.fromBytes(item.publicId());String content=item.originalProfile()==null?null:mediaUrls.url(spaceId,id,"ORIGINAL",item.originalProfile());String thumbnail=item.thumbnailProfile()==null?content:mediaUrls.url(spaceId,id,"THUMBNAIL",item.thumbnailProfile());return new SharedMedia(id,item.mediaType(),item.caption(),item.takenAt(),item.position(),content,thumbnail,null,null);}).toList();
        return new SharedDiary(row.getTitle(),row.getDiaryDate(),row.getContentHtml(),"html",row.getMoodKey(),media);
    }
    private PrivateShareMapper.DiaryRow manageable(SpaceAccess.SpaceContext space,UUID diaryId,long accountId){PrivateShareMapper.DiaryRow row=mapper.findManageableDiary(space.internalId(),BinaryUuid.toBytes(diaryId),accountId,"OWNER".equals(space.role()));if(row==null)throw V3Exception.notFound("DIARY_NOT_FOUND","日记不存在或无权管理分享");return row;}
    private Summary summary(PrivateShareMapper.ShareRow row){return new Summary(BinaryUuid.fromBytes(row.getPublicId()),row.getExpiresAt(),row.getMaxViews(),row.getViewCount(),row.getPasswordHash()!=null,row.getCreatedAt());}
    private byte[] sha256(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Created(UUID shareId,String sharePath,LocalDateTime expiresAt,Integer maxViews){}
    public record Summary(UUID shareId,LocalDateTime expiresAt,Integer maxViews,int viewCount,boolean passwordProtected,LocalDateTime createdAt){}
    public record SharedDiary(String title,LocalDate date,String content,String contentFormat,String moodKey,List<SharedMedia> media){}
    public record SharedMedia(UUID assetId,String mediaType,String caption,LocalDateTime takenAt,int position,String contentUrl,String thumbnailUrl,String posterUrl,String transcodedUrl){}
}
