package com.langxi.babydiary.diary.infrastructure;

import com.langxi.babydiary.diary.application.DiaryTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDiaryTemplateRepository implements DiaryTemplateRepository {
    private final DiaryTemplateMapper mapper;

    public MyBatisDiaryTemplateRepository(DiaryTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<TemplateData> findAll(long spaceId) {
        return mapper.findAll(spaceId).stream()
                .map(
                        row ->
                                new TemplateData(
                                        row.getPublicId(),
                                        row.getOwnerId(),
                                        row.getName(),
                                        row.getDescription(),
                                        row.getIcon(),
                                        row.getPromptText(),
                                        row.getContentHtml(),
                                        row.isBuiltin(),
                                        row.getUpdatedAt()))
                .toList();
    }

    @Override
    public void insert(NewTemplate template) {
        mapper.insert(
                new DiaryTemplateMapper.TemplateInsert(
                        template.publicId(),
                        template.spaceId(),
                        template.ownerId(),
                        template.name(),
                        template.description(),
                        template.icon(),
                        template.promptText(),
                        template.contentHtml()));
    }

    @Override
    public int update(
            long spaceId,
            byte[] publicId,
            long ownerId,
            String name,
            String description,
            String icon,
            String promptText,
            String contentHtml) {
        return mapper.update(
                spaceId, publicId, ownerId, name, description, icon, promptText, contentHtml);
    }

    @Override
    public int deactivate(long spaceId, byte[] publicId, long ownerId) {
        return mapper.deactivate(spaceId, publicId, ownerId);
    }
}
