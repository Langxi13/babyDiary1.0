package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.config.ClientReleaseProperties;
import com.langxi.babydiary.dto.ClientBootstrapVO;
import com.langxi.babydiary.service.ClientReleaseService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ClientBootstrapControllerTest {

    @Test
    void exposesNativeCompatibilityWithoutCaching() {
        ClientReleaseProperties properties = new ClientReleaseProperties();
        ResponseEntity<Result<ClientBootstrapVO>> response = controller(properties).bootstrap();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
        assertThat(response.getBody().getData().apiVersion()).isEqualTo(2);
        assertThat(response.getBody().getData().nativeSessionMode()).isEqualTo("COOKIE");
        assertThat(response.getBody().getData().serverVersion()).isEqualTo("1.0.0");
        assertThat(response.getBody().getData().androidUpdate().enabled()).isFalse();
        assertThat(response.getBody().getData().upload().maxImageBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(response.getBody().getData().upload().maxDiaryImages()).isEqualTo(50);
        assertThat(response.getBody().getData().upload().acceptedImageTypes())
                .containsExactly("image/gif", "image/jpeg", "image/png", "image/webp");
    }

    @Test
    void exposesOnlyCompleteHttpsOrSameOriginAndroidUpdates() {
        ClientReleaseProperties properties = new ClientReleaseProperties();
        ClientReleaseProperties.Android android = properties.getAndroid();
        android.setEnabled(true);
        android.setLatestVersionCode(2);
        android.setLatestVersionName("1.0.0-beta.2");
        android.setMinimumVersionCode(1);
        android.setDownloadUrl("/downloads/android/BabyDiary-1.0.0-beta.2.apk");
        android.setSha256("a".repeat(64));
        android.setReleaseNotes("增加应用内更新检测");

        ClientBootstrapVO.AndroidUpdate update = controller(properties).bootstrap().getBody().getData().androidUpdate();

        assertThat(update.enabled()).isTrue();
        assertThat(update.distribution()).isEqualTo("DIRECT");
        assertThat(update.latestVersionCode()).isEqualTo(2);
        assertThat(update.latestVersionName()).isEqualTo("1.0.0-beta.2");
        assertThat(update.downloadUrl()).startsWith("/downloads/android/");
        assertThat(update.sha256()).hasSize(64);
    }

    @Test
    void disablesUnsafeOrIncompleteAndroidUpdateConfiguration() {
        ClientReleaseProperties properties = new ClientReleaseProperties();
        ClientReleaseProperties.Android android = properties.getAndroid();
        android.setEnabled(true);
        android.setLatestVersionCode(2);
        android.setLatestVersionName("1.0.0-beta.2");
        android.setDownloadUrl("https://downloads.example.com/release.zip");
        android.setSha256("b".repeat(64));

        ClientBootstrapVO.AndroidUpdate update = controller(properties).bootstrap().getBody().getData().androidUpdate();

        assertThat(update.enabled()).isFalse();
        assertThat(update.downloadUrl()).isEmpty();

        android.setDownloadUrl("http://downloads.example.com/app.apk");
        update = controller(properties).bootstrap().getBody().getData().androidUpdate();
        assertThat(update.enabled()).isFalse();

        android.setDownloadUrl("https://downloads.example.com/app.apk");
        android.setMinimumVersionCode(0);
        update = controller(properties).bootstrap().getBody().getData().androidUpdate();
        assertThat(update.enabled()).isFalse();
    }

    private ClientBootstrapController controller(ClientReleaseProperties properties) {
        return new ClientBootstrapController(new ClientReleaseService(properties));
    }
}
