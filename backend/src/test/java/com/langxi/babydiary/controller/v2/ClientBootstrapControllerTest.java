package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.ClientBootstrapVO;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ClientBootstrapControllerTest {

    @Test
    void exposesNativeCompatibilityWithoutCaching() {
        ResponseEntity<Result<ClientBootstrapVO>> response = new ClientBootstrapController().bootstrap();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
        assertThat(response.getBody().getData().apiVersion()).isEqualTo(2);
        assertThat(response.getBody().getData().nativeSessionMode()).isEqualTo("COOKIE");
        assertThat(response.getBody().getData().upload().maxImageBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(response.getBody().getData().upload().maxDiaryImages()).isEqualTo(50);
        assertThat(response.getBody().getData().upload().acceptedImageTypes())
                .containsExactly("image/gif", "image/jpeg", "image/png", "image/webp");
    }
}
