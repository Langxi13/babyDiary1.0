package com.langxi.babydiary.v3.diary.api;

import com.langxi.babydiary.v3.diary.application.DiaryTemplateService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v3/spaces/{spaceId}/templates")
public class DiaryTemplateController {
    private final DiaryTemplateService templates; public DiaryTemplateController(DiaryTemplateService templates){this.templates=templates;}
    @GetMapping public List<DiaryTemplateService.Template> list(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId){return templates.list(spaceId,p.accountId());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DiaryTemplateService.Template create(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId,@Valid @RequestBody Request r){return templates.create(spaceId,p.accountId(),r.command());}
    @PutMapping("/{templateId}")
    public DiaryTemplateService.Template update(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId,@PathVariable UUID templateId,@Valid @RequestBody Request r){return templates.update(spaceId,templateId,p.accountId(),r.command());}
    @DeleteMapping("/{templateId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId,@PathVariable UUID templateId){templates.delete(spaceId,templateId,p.accountId());}
    public record Request(@NotBlank @Size(max=100) String name,@Size(max=500) String description,@Size(max=32) String icon,@Size(max=1000) String promptText,@NotBlank String contentHtml){DiaryTemplateService.Command command(){return new DiaryTemplateService.Command(name,description,icon,promptText,contentHtml);}}
}
