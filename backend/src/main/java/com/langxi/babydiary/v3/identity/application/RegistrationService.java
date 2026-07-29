package com.langxi.babydiary.v3.identity.application;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceGateway;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RegistrationService {
    private static final long DEFAULT_QUOTA = 5L * 1024 * 1024 * 1024;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{2,100}$");
    private final AccountGateway accounts;
    private final SpaceGateway spaces;
    private final InvitationCodeService invitations;
    private final PasswordEncoder passwords;

    public RegistrationService(AccountGateway accounts, SpaceGateway spaces, InvitationCodeService invitations,
                               PasswordEncoder passwords) {
        this.accounts = accounts;
        this.spaces = spaces;
        this.invitations = invitations;
        this.passwords = passwords;
    }

    @Transactional
    public void register(String username, String password, String confirmation, String invitationCode) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME.matcher(normalized).matches()) {
            throw V3Exception.badRequest("USERNAME_INVALID", "用户名仅支持字母、数字、点、下划线和短横线，长度为2至100个字符");
        }
        if (password == null || password.length() < 8 || password.length() > 200) {
            throw V3Exception.badRequest("PASSWORD_WEAK", "密码长度需为8至200个字符");
        }
        if (!password.equals(confirmation)) throw V3Exception.badRequest("PASSWORD_MISMATCH", "两次输入的密码不一致");
        if (!invitations.matchesForRegistration(invitationCode)) throw V3Exception.badRequest("INVITATION_CODE_INVALID", "邀请码无效");
        try {
            boolean firstAccount = accounts.countAccounts() == 0;
            long accountId = accounts.insertAccount(UUID.randomUUID(), normalized, passwords.encode(password));
            if (firstAccount) accounts.promoteToAdmin(accountId);
            long spaceId = spaces.insertPersonal(UUID.randomUUID(), normalized + "的日记", accountId, DEFAULT_QUOTA);
            spaces.insertOwner(spaceId, accountId);
            spaces.insertStorageUsage(spaceId);
        } catch (DuplicateKeyException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
    }
}
