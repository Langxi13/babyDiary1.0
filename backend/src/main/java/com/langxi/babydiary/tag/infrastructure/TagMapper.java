package com.langxi.babydiary.tag.infrastructure;

import com.langxi.babydiary.tag.application.TagRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper {
    @Select("""
            SELECT t.tag_id,t.public_id,s.public_id AS space_public_id,t.name,t.color
            FROM tag t JOIN diary_space s ON s.space_id=t.space_id
            WHERE t.space_id=#{spaceId}
            ORDER BY t.name,t.tag_id
            """)
    List<TagRow> findForSpace(long spaceId);

    @Select("""
            SELECT t.tag_id,t.public_id,s.public_id AS space_public_id,t.name,t.color
            FROM tag t JOIN diary_space s ON s.space_id=t.space_id
            WHERE t.space_id=#{spaceId} AND t.public_id=#{publicId}
            """)
    TagRow findByPublicId(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Insert("""
            INSERT INTO tag(public_id,space_id,name,color,created_by,created_at)
            VALUES(#{publicId},#{spaceId},#{name},#{color},#{createdBy},UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "tagId")
    void insert(TagInsert row);

    final class TagInsert {
        private Long tagId;
        private final byte[] publicId;
        private final long spaceId;
        private final String name;
        private final String color;
        private final long createdBy;

        public TagInsert(byte[] publicId, long spaceId, String name, String color, long createdBy) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.name = name;
            this.color = color;
            this.createdBy = createdBy;
        }

        public Long getTagId() { return tagId; }
        public void setTagId(Long tagId) { this.tagId = tagId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public String getName() { return name; }
        public String getColor() { return color; }
        public long getCreatedBy() { return createdBy; }
    }

    final class TagRow {
        private long tagId;
        private byte[] publicId;
        private byte[] spacePublicId;
        private String name;
        private String color;

        public TagRow() {
        }

        public long tagId() { return tagId; }
        public byte[] publicId() { return publicId; }
        public byte[] spacePublicId() { return spacePublicId; }
        public String name() { return name; }
        public String color() { return color; }

        public void setTagId(long tagId) { this.tagId = tagId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setSpacePublicId(byte[] spacePublicId) { this.spacePublicId = spacePublicId; }
        public void setName(String name) { this.name = name; }
        public void setColor(String color) { this.color = color; }
    }
}
