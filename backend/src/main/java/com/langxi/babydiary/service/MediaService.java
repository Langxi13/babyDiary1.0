package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.MediaAssetVO;
import com.langxi.babydiary.dto.MediaMetadataDTO;
import com.langxi.babydiary.entity.*;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.MediaMapper;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class MediaService {
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"), Map.entry("image/png", ".png"),
            Map.entry("image/gif", ".gif"), Map.entry("image/webp", ".webp"),
            Map.entry("audio/mpeg", ".mp3"), Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/ogg", ".ogg"), Map.entry("audio/wav", ".wav"),
            Map.entry("audio/x-wav", ".wav"), Map.entry("video/mp4", ".mp4"),
            Map.entry("video/webm", ".webm"), Map.entry("video/quicktime", ".mov"));

    private final MediaMapper mapper;
    private final SpaceService spaceService;
    private final CollaborationMapper diaryMapper;
    private final ObjectStorage storage;
    private final MediaAccessTokenService tokenService;
    private final AccountSecurityService accountSecurityService;

    public MediaService(MediaMapper mapper,
                        SpaceService spaceService,
                        CollaborationMapper diaryMapper,
                        ObjectStorage storage,
                        MediaAccessTokenService tokenService,
                        AccountSecurityService accountSecurityService) {
        this.mapper = mapper;
        this.spaceService = spaceService;
        this.diaryMapper = diaryMapper;
        this.storage = storage;
        this.tokenService = tokenService;
        this.accountSecurityService = accountSecurityService;
    }

    @Transactional
    public MediaAssetVO upload(String spacePublicId, Integer userId, MultipartFile file, String diaryPublicId,
                               String caption, String takenAt, String locationName,
                               BigDecimal latitude, BigDecimal longitude, String stepUpToken) throws IOException {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Diary targetDiary = null;
        if (diaryPublicId != null && !diaryPublicId.isBlank()) {
            targetDiary = diaryMapper.findDiary(space.getSpaceId(), diaryPublicId);
            if (targetDiary == null || targetDiary.getDeletedAt() != null
                    || ("PRIVATE".equals(targetDiary.getVisibility()) && !targetDiary.getUserId().equals(userId))) {
                throw new BusinessException(ErrorCode.DIARY_NOT_FOUND);
            }
            if (Boolean.TRUE.equals(targetDiary.getLocked())) {
                accountSecurityService.requireStepUp(userId, stepUpToken);
            }
        }
        validateUpload(file);
        spaceService.ensureStorageUsage(space.getSpaceId());
        long used = mapper.findUsedBytesForUpdate(space.getSpaceId());
        long quota = mapper.findQuotaBytes(space.getSpaceId());
        if (file.getSize() > quota - used) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "空间存储额度不足");
        }
        String contentType = normalizeType(file.getContentType());
        String publicId = UUID.randomUUID().toString();
        String key = "spaces/" + spacePublicId + "/" + LocalDate.now() + "/" + publicId + EXTENSIONS.get(contentType);
        try (InputStream input = file.getInputStream()) {
            storage.put(key, input, file.getSize(), contentType);
        }
        registerRollbackDelete(key);

        MediaAsset asset = new MediaAsset();
        asset.setPublicId(publicId);
        asset.setSpaceId(space.getSpaceId());
        asset.setOwnerUserId(userId);
        asset.setMediaType(mediaType(contentType));
        asset.setOriginalFilename(safeFilename(file.getOriginalFilename()));
        asset.setStorageProvider(storage.provider());
        asset.setStorageKey(key);
        asset.setContentType(contentType);
        asset.setSizeBytes(file.getSize());
        asset.setCaption(blankToNull(caption));
        asset.setTakenAt(parseTimestamp(takenAt));
        asset.setLocationName(blankToNull(locationName));
        asset.setLatitude(latitude);
        asset.setLongitude(longitude);
        asset.setStatus("PROCESSING");
        mapper.insertAsset(asset);
        mapper.addUsedBytes(space.getSpaceId(), file.getSize());

        if (targetDiary != null) {
            mapper.attachToDiary(targetDiary.getDiaryId(), asset.getAssetId(),
                    mapper.nextDiarySort(targetDiary.getDiaryId()));
        }
        return toVO(asset);
    }

    public List<MediaAssetVO> findByDiary(Integer diaryId) {
        return mapper.findByDiaryId(diaryId).stream().map(this::toVO).toList();
    }

    public Map<Integer, List<MediaAssetVO>> findByDiaries(List<Integer> diaryIds) {
        if (diaryIds == null || diaryIds.isEmpty()) return Collections.emptyMap();
        Map<Integer, List<MediaAssetVO>> result = new LinkedHashMap<>();
        diaryIds.forEach(diaryId -> result.put(diaryId, new ArrayList<>()));
        for (MediaAsset asset : mapper.findByDiaryIds(diaryIds)) {
            result.computeIfAbsent(asset.getDiaryId(), ignored -> new ArrayList<>()).add(toVO(asset));
        }
        return result;
    }

    public MediaAsset requireAccessible(String spacePublicId, String assetPublicId, Integer userId,
                                        String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        MediaAsset asset = mapper.findByPublicId(assetPublicId);
        if (asset == null || !space.getSpaceId().equals(asset.getSpaceId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (asset.getOwnerUserId().equals(userId)) {
            if (mapper.countLockedLinks(asset.getAssetId()) > 0) {
                accountSecurityService.requireStepUp(userId, stepUpToken);
            }
            return asset;
        }
        if (mapper.countAccessibleSharedLinks(asset.getAssetId()) == 0) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return asset;
    }

    @Transactional
    public MediaAssetVO updateMetadata(String spacePublicId, String assetPublicId, Integer userId,
                                       MediaMetadataDTO dto, String stepUpToken) {
        MediaAsset asset = requireAccessible(spacePublicId, assetPublicId, userId, stepUpToken);
        if (!asset.getOwnerUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        mapper.updateMetadata(asset.getAssetId(), blankToNull(dto.getCaption()), blankToNull(dto.getLocationName()),
                dto.getLatitude(), dto.getLongitude());
        return toVO(mapper.findByPublicId(assetPublicId));
    }

    @Transactional
    public void delete(String spacePublicId, String assetPublicId, Integer userId, String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        SpaceMember member = spaceService.requireMember(space, userId);
        MediaAsset asset = requireAccessible(spacePublicId, assetPublicId, userId, stepUpToken);
        if (!asset.getOwnerUserId().equals(userId) && !"OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (mapper.softDelete(asset.getAssetId()) == 1) {
            mapper.addUsedBytes(space.getSpaceId(), -asset.getSizeBytes());
            deleteAfterCommit(asset);
        }
    }

    @Transactional
    public void deleteOrphanedAfterDiaryPurge(List<MediaAsset> candidates) {
        if (candidates == null || candidates.isEmpty()) return;
        for (MediaAsset asset : candidates) {
            if (mapper.countDiaryLinks(asset.getAssetId()) == 0 && mapper.softDelete(asset.getAssetId()) == 1) {
                mapper.addUsedBytes(asset.getSpaceId(), -asset.getSizeBytes());
                deleteAfterCommit(asset);
            }
        }
    }

    public List<MediaAsset> findAssetsByDiary(Integer diaryId) {
        return mapper.findByDiaryId(diaryId);
    }

    public StoredObject openOriginalForExport(MediaAsset asset) throws IOException {
        return storage.get(asset.getStorageKey());
    }

    public StoredObject openSigned(String publicId, String variant, long expires, String signature) throws IOException {
        tokenService.verify(publicId, variant, expires, signature);
        MediaAsset asset = mapper.findByPublicId(publicId);
        if (asset == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        String key = keyFor(asset, variant);
        if (key == null) throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        return storage.get(key);
    }

    public String contentType(String publicId, String variant) {
        MediaAsset asset = mapper.findByPublicId(publicId);
        if (asset == null) return "application/octet-stream";
        return switch (variant) {
            case "thumbnail", "poster" -> "image/jpeg";
            case "waveform" -> "image/png";
            case "transcoded" -> "video/mp4";
            default -> asset.getContentType();
        };
    }

    public MediaAssetVO toVO(MediaAsset asset) {
        MediaAssetVO vo = MediaAssetVO.from(asset);
        vo.setContentUrl(tokenService.sign(asset.getPublicId(), "original").url());
        if (asset.getThumbnailKey() != null) vo.setThumbnailUrl(tokenService.sign(asset.getPublicId(), "thumbnail").url());
        if (asset.getPosterKey() != null) vo.setPosterUrl(tokenService.sign(asset.getPublicId(), "poster").url());
        if (asset.getWaveformKey() != null) vo.setWaveformUrl(tokenService.sign(asset.getPublicId(), "waveform").url());
        if (asset.getTranscodedKey() != null) vo.setTranscodedUrl(tokenService.sign(asset.getPublicId(), "transcoded").url());
        return vo;
    }

    private void validateUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "媒体文件不能为空");
        String type = normalizeType(file.getContentType());
        if (!EXTENSIONS.containsKey(type)) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        long max = type.startsWith("image/") ? 25L * 1024 * 1024 : 256L * 1024 * 1024;
        if (file.getSize() > max) throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        byte[] header = new byte[16];
        int read;
        try (InputStream input = file.getInputStream()) {
            read = input.read(header);
        }
        if (!validSignature(type, header, read)) throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "媒体内容与格式不匹配");
    }

    private boolean validSignature(String type, byte[] bytes, int length) {
        if (type.equals("image/jpeg")) return length >= 3 && u(bytes[0]) == 0xff && u(bytes[1]) == 0xd8 && u(bytes[2]) == 0xff;
        if (type.equals("image/png")) return prefix(bytes, length, 0x89,0x50,0x4e,0x47);
        if (type.equals("image/gif")) return length >= 3 && bytes[0]=='G' && bytes[1]=='I' && bytes[2]=='F';
        if (type.equals("image/webp")) return length >= 12 && ascii(bytes,0,"RIFF") && ascii(bytes,8,"WEBP");
        if (type.equals("audio/mpeg")) return length >= 3 && (ascii(bytes,0,"ID3") || (u(bytes[0])==0xff && (u(bytes[1])&0xe0)==0xe0));
        if (type.equals("audio/wav") || type.equals("audio/x-wav")) return length >= 12 && ascii(bytes,0,"RIFF") && ascii(bytes,8,"WAVE");
        if (type.equals("audio/ogg")) return ascii(bytes,0,"OggS");
        if (type.equals("video/webm")) return length >= 4 && u(bytes[0])==0x1a && u(bytes[1])==0x45 && u(bytes[2])==0xdf && u(bytes[3])==0xa3;
        if (type.equals("video/mp4") || type.equals("video/quicktime") || type.equals("audio/mp4")) return length >= 12 && ascii(bytes,4,"ftyp");
        return false;
    }

    private String keyFor(MediaAsset asset, String variant) {
        return switch (variant) {
            case "original" -> asset.getStorageKey();
            case "thumbnail" -> asset.getThumbnailKey();
            case "poster" -> asset.getPosterKey();
            case "waveform" -> asset.getWaveformKey();
            case "transcoded" -> asset.getTranscodedKey();
            default -> null;
        };
    }

    private void registerRollbackDelete(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try { storage.delete(key); } catch (IOException ignored) { }
                }
            }
        });
    }

    private void deleteAfterCommit(MediaAsset asset) {
        Runnable deletion = () -> Arrays.asList(asset.getStorageKey(), asset.getThumbnailKey(), asset.getPosterKey(),
                        asset.getWaveformKey(), asset.getTranscodedKey()).stream().filter(Objects::nonNull).forEach(key -> {
                    try { storage.delete(key); } catch (IOException ignored) { }
                });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deletion.run(); }
            });
        } else deletion.run();
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Timestamp.from(Instant.parse(value)); }
        catch (DateTimeParseException exception) { throw new BusinessException(ErrorCode.BAD_REQUEST, "拍摄时间格式无效"); }
    }

    private String safeFilename(String value) {
        if (value == null) return null;
        String name = value.replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "");
        return name.substring(0, Math.min(255, name.length()));
    }

    private String normalizeType(String value) {
        if (value == null) return "";
        int separator = value.indexOf(';');
        return (separator < 0 ? value : value.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
    }

    private String mediaType(String contentType) {
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "VIDEO";
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int i=0;i<value.length();i++) if (bytes[offset+i] != (byte)value.charAt(i)) return false;
        return true;
    }
    private boolean prefix(byte[] bytes, int length, int... expected) {
        if (length < expected.length) return false;
        for (int i=0;i<expected.length;i++) if (u(bytes[i]) != expected[i]) return false;
        return true;
    }
    private int u(byte value) { return value & 0xff; }
}
