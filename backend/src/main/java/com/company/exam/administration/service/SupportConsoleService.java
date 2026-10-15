package com.company.exam.administration.service;

import com.company.exam.administration.dto.AdministrationDtos;
import com.company.exam.common.audit.SecurityEventLogger;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.communication.service.NotificationService;
import com.company.exam.identity.api.AdminUserDirectory;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support console operations ({@code specs/20-P11-Administration-V1.5.md §34-§36}).
 *
 * <p>Support is a focused operational role, not a second administrator ({@code §34}). Contact data is
 * masked by default; unmasking requires {@code SUPPORT_USER_CONTACT} ({@code §35}). Support never
 * sees password hashes, reset tokens, JWTs, provider secrets, or unrestricted answer keys
 * ({@code §36}); every cross-domain action is performed through the owning service.
 */
@Service
public class SupportConsoleService {

    private final AdminUserDirectory userDirectory;
    private final NotificationService notificationService;
    private final SecurityEventLogger audit;

    public SupportConsoleService(AdminUserDirectory userDirectory, NotificationService notificationService,
            SecurityEventLogger audit) {
        this.userDirectory = userDirectory;
        this.notificationService = notificationService;
        this.audit = audit;
    }

    /**
     * A support user view with default masking ({@code §35}).
     *
     * @param unmask whether the caller holds {@code SUPPORT_USER_CONTACT}
     */
    @Transactional(readOnly = true)
    public AdministrationDtos.SupportUserView userView(UUID userId, boolean unmask) {
        AdminUserDirectory.AdminUserView user = userDirectory.find(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
        return new AdministrationDtos.SupportUserView(user.userId(), user.firstName(), user.lastName(),
                unmask ? user.email() : maskEmail(user.email()),
                unmask ? user.mobile() : maskMobile(user.mobile()), user.status(), unmask);
    }

    /**
     * Sends an ad-hoc notification to a single user through the Communication service, which owns
     * notification creation. Audited as a privileged support action ({@code §35}).
     */
    @Transactional
    public void sendNotification(UUID userId, String title, String message, UUID actorId) {
        notificationService.notifyUsers(java.util.List.of(userId), title, message);
        audit.audit(actorId, "NOTIFY", "User", userId, "Support sent an ad-hoc notification");
    }

    /** {@code j***@example.com} — the local part beyond the first character is hidden ({@code §35}). */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** {@code +91*****1234} — the country prefix and the last four digits remain visible ({@code §35}). */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return mobile;
        }
        String digits = mobile;
        if (digits.length() <= 6) {
            return "***";
        }
        String prefix = digits.startsWith("+") ? digits.substring(0, Math.min(3, digits.length())) : "";
        String suffix = digits.substring(digits.length() - 4);
        return prefix + "*****" + suffix;
    }
}
