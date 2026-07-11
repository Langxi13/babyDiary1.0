package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.DiaryTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DiaryTemplateMapper {
    @Select("SELECT template_id templateId,public_id publicId,space_id spaceId,owner_user_id ownerUserId,template_key templateKey," +
            "name,description,icon,prompt_text promptText,content_html contentHtml,builtin,active,created_at createdAt,updated_at updatedAt " +
            "FROM diary_template WHERE active=1 AND (builtin=1 OR space_id=#{spaceId}) ORDER BY builtin DESC,updated_at DESC")
    List<DiaryTemplate> list(@Param("spaceId") Long spaceId);

    @Select("SELECT template_id templateId,public_id publicId,space_id spaceId,owner_user_id ownerUserId,template_key templateKey," +
            "name,description,icon,prompt_text promptText,content_html contentHtml,builtin,active,created_at createdAt,updated_at updatedAt " +
            "FROM diary_template WHERE public_id=#{publicId} AND active=1")
    DiaryTemplate findByPublicId(@Param("publicId") String publicId);

    @Insert("INSERT INTO diary_template(public_id,space_id,owner_user_id,name,description,icon,prompt_text,content_html,builtin) " +
            "VALUES(#{publicId},#{spaceId},#{ownerUserId},#{name},#{description},#{icon},#{promptText},#{contentHtml},0)")
    @Options(useGeneratedKeys = true, keyProperty = "templateId")
    void insert(DiaryTemplate template);

    @Update("UPDATE diary_template SET name=#{name},description=#{description},icon=#{icon},prompt_text=#{promptText},content_html=#{contentHtml} " +
            "WHERE template_id=#{templateId} AND owner_user_id=#{ownerUserId} AND builtin=0 AND active=1")
    int update(DiaryTemplate template);

    @Update("UPDATE diary_template SET active=0 WHERE template_id=#{templateId} AND owner_user_id=#{userId} AND builtin=0")
    int deactivate(@Param("templateId") Long templateId, @Param("userId") Integer userId);
}
