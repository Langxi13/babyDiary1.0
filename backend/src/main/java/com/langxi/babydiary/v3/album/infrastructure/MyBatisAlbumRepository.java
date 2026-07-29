package com.langxi.babydiary.v3.album.infrastructure;

import com.langxi.babydiary.v3.album.application.AlbumRepository;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisAlbumRepository implements AlbumRepository {
    private final AlbumMapper mapper;

    public MyBatisAlbumRepository(AlbumMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<GroupRow> findGroups(long spaceId) {
        return mapper.findGroups(spaceId).stream().map(row -> new GroupRow(row.groupId(),
                BinaryUuid.fromBytes(row.publicId()), row.name(), row.sortOrder())).toList();
    }

    @Override
    public List<AlbumRow> findAlbums(long spaceId) {
        return mapper.findAlbums(spaceId).stream().map(this::album).toList();
    }

    @Override
    public Optional<AlbumRow> findAlbum(long spaceId, UUID albumId) {
        return Optional.ofNullable(mapper.findAlbum(spaceId, BinaryUuid.toBytes(albumId))).map(this::album);
    }

    @Override
    public List<UUID> findMediaPublicIds(long spaceId, UUID albumId, long accountId, int offset, int limit) {
        return mapper.findMediaPublicIds(spaceId, BinaryUuid.toBytes(albumId), accountId, offset, limit).stream()
                .map(BinaryUuid::fromBytes).toList();
    }

    @Override
    public long countMedia(long spaceId, UUID albumId, long accountId) {
        return mapper.countMedia(spaceId, BinaryUuid.toBytes(albumId), accountId);
    }

    @Override
    public long insertGroup(NewGroup group) {
        AlbumMapper.GroupInsert row = new AlbumMapper.GroupInsert(BinaryUuid.toBytes(group.publicId()), group.spaceId(),
                group.name(), group.sortOrder(), group.createdBy());
        mapper.insertGroup(row);
        if (row.getGroupId() == null) throw new IllegalStateException("Album group insert returned no ID");
        return row.getGroupId();
    }

    @Override public boolean updateGroup(long spaceId, UUID groupId, String name) {
        return mapper.updateGroup(spaceId, BinaryUuid.toBytes(groupId), name) == 1;
    }

    @Override public boolean deleteGroup(long spaceId, UUID groupId) {
        return mapper.deleteGroup(spaceId, BinaryUuid.toBytes(groupId)) == 1;
    }

    @Override
    public long insertAlbum(NewAlbum album) {
        AlbumMapper.AlbumInsert row = new AlbumMapper.AlbumInsert(BinaryUuid.toBytes(album.publicId()), album.spaceId(),
                album.groupId(), album.createdBy(), album.name(), album.description(), album.type(), album.coverAssetId(),
                album.sortOrder());
        mapper.insertAlbum(row);
        if (row.getAlbumId() == null) throw new IllegalStateException("Album insert returned no ID");
        return row.getAlbumId();
    }

    @Override public boolean updateAlbum(long spaceId, UUID albumId, Long groupId, String name, String description) {
        return mapper.updateAlbum(spaceId, BinaryUuid.toBytes(albumId), groupId, name, description) == 1;
    }

    @Override public boolean softDeleteAlbum(long spaceId, UUID albumId) {
        return mapper.softDeleteAlbum(spaceId, BinaryUuid.toBytes(albumId)) == 1;
    }

    @Override public void insertMedia(long spaceId, long albumId, long assetId, int position) { mapper.insertMedia(spaceId, albumId, assetId, position); }
    @Override public void deleteMedia(long spaceId, long albumId, long assetId) { mapper.deleteMedia(spaceId, albumId, assetId); }
    @Override public void deleteAllMedia(long spaceId, long albumId) { mapper.deleteAllMedia(spaceId, albumId); }
    @Override public void updateCover(long spaceId, long albumId, Long assetId) { mapper.updateCover(spaceId, albumId, assetId); }
    @Override public void addFavorite(long spaceId, long accountId, long assetId) { mapper.addFavorite(spaceId, accountId, assetId); }
    @Override public void removeFavorite(long spaceId, long accountId, long assetId) { mapper.removeFavorite(spaceId, accountId, assetId); }

    @Override
    public List<UUID> findFavoritePublicIds(long spaceId, long accountId, int offset, int limit) {
        return mapper.findFavoritePublicIds(spaceId, accountId, offset, limit).stream().map(BinaryUuid::fromBytes).toList();
    }

    @Override
    public long countFavoriteMedia(long spaceId, long accountId) {
        return mapper.countFavoriteMedia(spaceId, accountId);
    }

    @Override
    public List<UUID> findLibraryPublicIds(long spaceId, long accountId, int offset, int limit) {
        return mapper.findLibraryPublicIds(spaceId, accountId, offset, limit).stream()
                .map(BinaryUuid::fromBytes).toList();
    }

    @Override
    public long countLibraryImages(long spaceId, long accountId) {
        return mapper.countLibraryImages(spaceId, accountId);
    }

    private AlbumRow album(AlbumMapper.AlbumRow row) {
        return new AlbumRow(row.albumId(), BinaryUuid.fromBytes(row.publicId()), row.groupId(),
                row.groupPublicId() == null ? null : BinaryUuid.fromBytes(row.groupPublicId()),
                row.type(), row.name(), row.description(),
                row.coverPublicId() == null ? null : BinaryUuid.fromBytes(row.coverPublicId()), row.mediaCount());
    }
}
