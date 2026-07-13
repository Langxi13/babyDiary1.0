package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.ClientBootstrapVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/client")
public class ClientBootstrapController {

    @GetMapping("/bootstrap")
    public ResponseEntity<Result<ClientBootstrapVO>> bootstrap() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.success(ClientBootstrapVO.current()));
    }
}
