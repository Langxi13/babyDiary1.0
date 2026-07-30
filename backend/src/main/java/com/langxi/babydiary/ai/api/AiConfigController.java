package com.langxi.babydiary.ai.api;

import com.langxi.babydiary.ai.application.AiConfigService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v3/admin/ai")
public class AiConfigController {
    private final AiConfigService ai;

    public AiConfigController(AiConfigService ai) {
        this.ai = ai;
    }

    @GetMapping
    public AiConfigService.ConfigView view(@AuthenticationPrincipal AccountPrincipal principal) {
        return ai.view(principal);
    }

    @PostMapping
    public AiConfigService.ConfigView save(@AuthenticationPrincipal AccountPrincipal principal,
                                           @Valid @RequestBody AiConfigRequest request) {
        return ai.save(principal, new AiConfigService.Command(request.enabled(), request.baseUrl(), request.model(),
                request.apiKey(), request.timeoutSeconds()));
    }

    @PostMapping("/test")
    public TestResponse test(@AuthenticationPrincipal AccountPrincipal principal) {
        return new TestResponse(ai.test(principal));
    }

    @GetMapping("/models")
    public List<String> models(@AuthenticationPrincipal AccountPrincipal principal) {
        return ai.models(principal);
    }

    public record AiConfigRequest(boolean enabled, @Size(max = 500) String baseUrl, @Size(max = 128) String model,
                                  @Size(max = 500) String apiKey, int timeoutSeconds) {
    }

    public record TestResponse(String result) {
    }
}
