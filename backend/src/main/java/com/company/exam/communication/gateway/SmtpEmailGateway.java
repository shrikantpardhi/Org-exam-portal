package com.company.exam.communication.gateway;

import com.company.exam.communication.config.CommunicationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP {@link EmailGateway} adapter, used to deliver real mail to a local Mailpit.
 *
 * <p>{@code specs/19-P10} §29 names an SMTP client as a legitimate provider and §810 requires
 * provider clients to stay below the {@code EmailGateway} boundary. This class is the only place in
 * the codebase that imports Jakarta Mail; the Communication domain still depends on the interface
 * alone, so swapping in a hosted provider is a second adapter and no domain change.
 *
 * <p><b>It is not enabled by default.</b> {@code app.communication.provider} must be set to
 * {@code SMTP}; otherwise {@link FakeEmailGateway} remains the active provider, so no test or CI run
 * can start sending mail because a compose file changed.
 *
 * <p><b>Failure classification follows the frozen retry contract (§34).</b> {@code MailException} is
 * transient — the provider was unreachable or errored, and the bounded backoff should retry. A
 * malformed address is permanent: retrying it would burn the whole budget to fail identically every
 * time.
 */
@Component
@ConditionalOnProperty(name = "app.communication.provider", havingValue = "SMTP")
public class SmtpEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailGateway.class);

    private static final EmailProviderId PROVIDER = new EmailProviderId("SMTP");

    private final JavaMailSender mailSender;
    private final String fromAddress;

    @SuppressWarnings("unused")
    private final CommunicationProperties properties;

    public SmtpEmailGateway(JavaMailSender mailSender, CommunicationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.fromAddress = properties.getSmtp().getFrom();
    }

    @Override
    public EmailProviderId provider() {
        return PROVIDER;
    }

    @Override
    public EmailSendResult send(RenderedEmail email) {
        if (email == null || email.toAddress() == null || email.toAddress().isBlank()) {
            // Permanent: an address cannot become valid by retrying.
            log.warn("SMTP send rejected: no recipient address");
            return new EmailSendResult(null, SendOutcome.FAILED, false);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(email.toAddress());
            helper.setSubject(email.subject() == null ? "" : email.subject());
            // The rendered body is HTML. Content is produced by MARKDOWN_LATEX_V1 rendering upstream
            // and already carries no raw HTML or executable content.
            helper.setText(email.htmlBody() == null ? "" : email.htmlBody(), true);
            mailSender.send(message);
            // SMTP gives no durable provider reference, so one is synthesized for correlation. It is
            // not a query handle: query() therefore returns empty rather than pretending to look it up.
            String reference = "SMTP-" + UUID.randomUUID();
            log.debug("SMTP accepted message for providerReference={}", reference);
            return new EmailSendResult(reference, SendOutcome.SENT, false);
        } catch (MessagingException | MailException failure) {
            // Transient: connection refused, auth failure, 4xx/5xx from the relay. The bounded
            // backoff retries, and maxAttempts bounds the damage (§33).
            log.warn("SMTP send failed, treating as retryable: {}", failure.getMessage());
            return new EmailSendResult(null, SendOutcome.FAILED, true);
        }
    }

    @Override
    public Optional<EmailSendResult> query(String providerReference) {
        // SMTP has no status endpoint (§38). Returning a fabricated result would be worse than
        // returning none: the retry path already handles an ambiguous outcome.
        return Optional.empty();
    }
}
