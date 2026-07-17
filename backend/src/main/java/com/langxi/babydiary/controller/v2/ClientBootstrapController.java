package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.ClientBootstrapVO;
import com.langxi.babydiary.service.ClientReleaseService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/client")
public class ClientBootstrapController {

    private final ClientReleaseService clientReleaseService;

    public ClientBootstrapController(ClientReleaseService clientReleaseService) {
        this.clientReleaseService = clientReleaseService;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<Result<ClientBootstrapVO>> bootstrap() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.success(clientReleaseService.bootstrap()));
    }
}
