package com.langxi.babydiary.service;

import com.langxi.babydiary.dto.InvitationCodeVO;
import com.langxi.babydiary.entity.SystemInvitationConfig;
import com.langxi.babydiary.mapper.SystemInvitationConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationCodeServiceTest {
    @Mock
    private SystemInvitationConfigMapper configMapper;

    @Mock
    private InvitationCodeCrypto crypto;

    @Mock
    private StepUpTokenVerifier stepUpTokenVerifier;

    private InvitationCodeService service;

    @BeforeEach
    void setUp() {
        service = new InvitationCodeService(configMapper, crypto, stepUpTokenVerifier, "bootstrap-code");
    }

    @Test
    void importsBootstrapCodeOnlyWhenConfigurationIsMissing() {
        SystemInvitationConfig stored = config("encrypted-bootstrap");
        when(configMapper.findConfig()).thenReturn(null, stored);
        when(crypto.encrypt("bootstrap-code")).thenReturn("encrypted-bootstrap");

        service.initializeFromBootstrap();

        ArgumentCaptor<SystemInvitationConfig> captor = ArgumentCaptor.forClass(SystemInvitationConfig.class);
        verify(configMapper).insertIfAbsent(captor.capture());
        assertThat(captor.getValue().getEncryptedCode()).isEqualTo("encrypted-bootstrap");
        assertThat(captor.getValue().getUpdatedBy()).isNull();
    }

    @Test
    void existingDatabaseConfigurationIsNeverOverwrittenByBootstrapCode() {
        when(configMapper.findConfig()).thenReturn(config("existing-encrypted-code"));
        when(crypto.decrypt("existing-encrypted-code")).thenReturn("existing-code");

        service.initializeFromBootstrap();

        verify(configMapper, never()).insertIfAbsent(any());
        verify(crypto, never()).encrypt(any());
        verify(crypto).decrypt("existing-encrypted-code");
    }

    @Test
    void missingBootstrapCodeFailsFreshStartupClearly() {
        service = new InvitationCodeService(configMapper, crypto, stepUpTokenVerifier, " ");
        when(configMapper.findConfig()).thenReturn(null);

        assertThatThrownBy(service::initializeFromBootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVITATION_CODE");
    }

    @Test
    void invitationMatchingUsesTheCurrentEncryptedDatabaseValue() {
        when(configMapper.findConfigForShare()).thenReturn(config("encrypted-current"));
        when(crypto.decrypt("encrypted-current")).thenReturn("current-code");

        assertThat(service.matches("current-code")).isTrue();
        assertThat(service.matches("old-code")).isFalse();
    }

    @Test
    void visibleCodeRequiresStepUpAndReturnsDecryptedValue() {
        SystemInvitationConfig config = config("encrypted-current");
        config.setUpdatedAt(Timestamp.from(Instant.parse("2026-07-12T08:00:00Z")));
        when(configMapper.findConfig()).thenReturn(config);
        when(crypto.decrypt("encrypted-current")).thenReturn("current-code");

        InvitationCodeVO result = service.getVisibleCode(7, "step-token");

        verify(stepUpTokenVerifier).require(7, "step-token");
        assertThat(result.invitationCode()).isEqualTo("current-code");
        assertThat(result.updatedAt()).isEqualTo(config.getUpdatedAt());
    }

    @Test
    void rotationCreatesUrlSafeCodeAndRecordsTheAdministrator() {
        SystemInvitationConfig updated = config("encrypted-new");
        updated.setUpdatedBy(7);
        updated.setUpdatedAt(Timestamp.from(Instant.parse("2026-07-12T09:00:00Z")));
        when(crypto.encrypt(any())).thenReturn("encrypted-new");
        when(configMapper.findConfig()).thenReturn(updated);

        InvitationCodeVO result = service.rotate(7, "step-token");

        verify(stepUpTokenVerifier).require(7, "step-token");
        ArgumentCaptor<SystemInvitationConfig> captor = ArgumentCaptor.forClass(SystemInvitationConfig.class);
        verify(configMapper).upsertConfig(captor.capture());
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(7);
        assertThat(captor.getValue().getEncryptedCode()).isEqualTo("encrypted-new");
        assertThat(result.invitationCode()).matches("[A-Za-z0-9_-]{32}");
        assertThat(result.updatedAt()).isEqualTo(updated.getUpdatedAt());
    }

    private SystemInvitationConfig config(String encryptedCode) {
        SystemInvitationConfig config = new SystemInvitationConfig();
        config.setConfigId(1);
        config.setEncryptedCode(encryptedCode);
        return config;
    }
}
