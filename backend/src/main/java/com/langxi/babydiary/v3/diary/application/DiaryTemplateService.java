package com.langxi.babydiary.v3.diary.application;

import com.langxi.babydiary.v3.diary.infrastructure.DiaryTemplateMapper;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DiaryTemplateService {
    private final SpaceAccess spaces; private final DiaryTemplateMapper mapper; private final DiaryContentPolicy contentPolicy;
    public DiaryTemplateService(SpaceAccess spaces, DiaryTemplateMapper mapper, DiaryContentPolicy contentPolicy) {
        this.spaces=spaces;this.mapper=mapper;this.contentPolicy=contentPolicy;
    }
    public List<Template> list(UUID spaceId,long accountId){
        SpaceAccess.SpaceContext space=spaces.requireMember(spaceId,accountId);
        return mapper.findAll(space.internalId()).stream().map(row->response(row,accountId)).toList();
    }
    @Transactional
    public Template create(UUID spaceId,long accountId,Command command){
        SpaceAccess.SpaceContext space=spaces.requireWriter(spaceId,accountId); Values values=values(command); UUID id=UUID.randomUUID();
        mapper.insert(new DiaryTemplateMapper.TemplateInsert(BinaryUuid.toBytes(id),space.internalId(),accountId,values.name,
                values.description,values.icon,values.promptText,values.contentHtml));
        return mapper.findAll(space.internalId()).stream().filter(row->java.util.Arrays.equals(row.getPublicId(),BinaryUuid.toBytes(id)))
                .findFirst().map(row->response(row,accountId)).orElseThrow();
    }
    @Transactional
    public Template update(UUID spaceId,UUID templateId,long accountId,Command command){
        SpaceAccess.SpaceContext space=spaces.requireWriter(spaceId,accountId); Values values=values(command);
        if(mapper.update(space.internalId(),BinaryUuid.toBytes(templateId),accountId,values.name,values.description,values.icon,values.promptText,values.contentHtml)!=1)
            throw V3Exception.notFound("TEMPLATE_NOT_FOUND","模板不存在或无权修改");
        return mapper.findAll(space.internalId()).stream().filter(row->java.util.Arrays.equals(row.getPublicId(),BinaryUuid.toBytes(templateId)))
                .findFirst().map(row->response(row,accountId)).orElseThrow();
    }
    @Transactional
    public void delete(UUID spaceId,UUID templateId,long accountId){
        SpaceAccess.SpaceContext space=spaces.requireWriter(spaceId,accountId);
        if(mapper.deactivate(space.internalId(),BinaryUuid.toBytes(templateId),accountId)!=1)
            throw V3Exception.notFound("TEMPLATE_NOT_FOUND","模板不存在或无权删除");
    }
    private Values values(Command command){
        String name=trim(command.name(),100); if(name==null) throw V3Exception.badRequest("TEMPLATE_NAME_REQUIRED","模板名称不能为空");
        DiaryContentPolicy.Content content=contentPolicy.normalize(command.contentHtml());
        if(content.text().isBlank()) throw V3Exception.badRequest("TEMPLATE_CONTENT_REQUIRED","模板内容不能为空");
        return new Values(name,trim(command.description(),500),trim(command.icon(),32)==null?"Notebook":trim(command.icon(),32),
                trim(command.promptText(),1000),content.html());
    }
    private Template response(DiaryTemplateMapper.TemplateRow row,long accountId){
        return new Template(BinaryUuid.fromBytes(row.getPublicId()),BinaryUuid.fromBytes(row.getPublicId()),row.getName(),
                row.getDescription(),row.getIcon(),row.getPromptText(),row.getContentHtml(),row.isBuiltin(),
                !row.isBuiltin()&&row.getOwnerId()!=null&&row.getOwnerId()==accountId);
    }
    private String trim(String value,int max){if(value==null||value.trim().isEmpty())return null;String v=value.trim();return v.length()<=max?v:v.substring(0,max);}
    public record Command(String name,String description,String icon,String promptText,String contentHtml){}
    public record Template(UUID id,UUID templateId,String name,String description,String icon,String promptText,String contentHtml,boolean builtin,boolean editable){}
    private record Values(String name,String description,String icon,String promptText,String contentHtml){}
}
