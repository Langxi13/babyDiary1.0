package com.langxi.babydiary.diary.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiaryTemplateMapper {
    @Select(
            """
            SELECT template_id,public_id,space_id,owner_id,template_key,name,description,icon,prompt_text,
                   content_html,builtin,active,created_at,updated_at
            FROM diary_template
            WHERE active=1 AND (builtin=1 OR space_id=#{spaceId})
            ORDER BY builtin DESC,name,template_id
            """)
    List<TemplateRow> findAll(long spaceId);

    @Insert(
            """
            INSERT INTO diary_template(public_id,space_id,owner_id,name,description,icon,prompt_text,content_html,builtin,active)
            VALUES(#{publicId},#{spaceId},#{ownerId},#{name},#{description},#{icon},#{promptText},#{contentHtml},0,1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "templateId")
    void insert(TemplateInsert row);

    @Update(
            """
            UPDATE diary_template SET name=#{name},description=#{description},icon=#{icon},
                   prompt_text=#{promptText},content_html=#{contentHtml},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{publicId} AND owner_id=#{ownerId}
              AND builtin=0 AND active=1
            """)
    int update(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("ownerId") long ownerId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("icon") String icon,
            @Param("promptText") String promptText,
            @Param("contentHtml") String contentHtml);

    @Update(
            "UPDATE diary_template SET active=0,updated_at=UTC_TIMESTAMP(6) "
                    + "WHERE space_id=#{spaceId} AND public_id=#{publicId} AND owner_id=#{ownerId} AND builtin=0 AND active=1")
    int deactivate(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("ownerId") long ownerId);

    final class TemplateInsert {
        private Long templateId;
        private final byte[] publicId;
        private final long spaceId;
        private final long ownerId;
        private final String name;
        private final String description;
        private final String icon;
        private final String promptText;
        private final String contentHtml;

        public TemplateInsert(
                byte[] publicId,
                long spaceId,
                long ownerId,
                String name,
                String description,
                String icon,
                String promptText,
                String contentHtml) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.ownerId = ownerId;
            this.name = name;
            this.description = description;
            this.icon = icon;
            this.promptText = promptText;
            this.contentHtml = contentHtml;
        }

        public Long getTemplateId() {
            return templateId;
        }

        public void setTemplateId(Long v) {
            templateId = v;
        }

        public byte[] getPublicId() {
            return publicId;
        }

        public long getSpaceId() {
            return spaceId;
        }

        public long getOwnerId() {
            return ownerId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }

        public String getPromptText() {
            return promptText;
        }

        public String getContentHtml() {
            return contentHtml;
        }
    }

    final class TemplateRow {
        private long templateId;
        private byte[] publicId;
        private Long spaceId;
        private Long ownerId;
        private String templateKey;
        private String name;
        private String description;
        private String icon;
        private String promptText;
        private String contentHtml;
        private boolean builtin;
        private boolean active;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public TemplateRow() {}

        public long getTemplateId() {
            return templateId;
        }

        public byte[] getPublicId() {
            return publicId;
        }

        public Long getSpaceId() {
            return spaceId;
        }

        public Long getOwnerId() {
            return ownerId;
        }

        public String getTemplateKey() {
            return templateKey;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getIcon() {
            return icon;
        }

        public String getPromptText() {
            return promptText;
        }

        public String getContentHtml() {
            return contentHtml;
        }

        public boolean isBuiltin() {
            return builtin;
        }

        public boolean isActive() {
            return active;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setTemplateId(long v) {
            templateId = v;
        }

        public void setPublicId(byte[] v) {
            publicId = v;
        }

        public void setSpaceId(Long v) {
            spaceId = v;
        }

        public void setOwnerId(Long v) {
            ownerId = v;
        }

        public void setTemplateKey(String v) {
            templateKey = v;
        }

        public void setName(String v) {
            name = v;
        }

        public void setDescription(String v) {
            description = v;
        }

        public void setIcon(String v) {
            icon = v;
        }

        public void setPromptText(String v) {
            promptText = v;
        }

        public void setContentHtml(String v) {
            contentHtml = v;
        }

        public void setBuiltin(boolean v) {
            builtin = v;
        }

        public void setActive(boolean v) {
            active = v;
        }

        public void setCreatedAt(LocalDateTime v) {
            createdAt = v;
        }

        public void setUpdatedAt(LocalDateTime v) {
            updatedAt = v;
        }
    }
}
