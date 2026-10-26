package com.company.exam.communication.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.exam.communication.config.CommunicationProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * The SMTP adapter's failure classification and its boundary (ADR-0048).
 *
 * <p>The classification matters more than the happy path: {@code specs/19-P10} §34 splits transient
 * from permanent failures, and the retry worker spends its bounded budget accordingly. A permanent
 * failure mis-labelled retryable burns five attempts and delays the operator's visibility; a
 * transient failure mis-labelled permanent drops a real message.
 */
class SmtpEmailGatewayTest {

    private final CommunicationProperties properties = new CommunicationProperties();

    private SmtpEmailGateway gateway(JavaMailSender sender) {
        return new SmtpEmailGateway(sender, properties);
    }

    private JavaMailSender senderReturning(MimeMessage message) {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(message);
        return sender;
    }

    private EmailGateway.RenderedEmail email() {
        return new EmailGateway.RenderedEmail("student@example.test", "Subject", "<p>Body</p>");
    }

    @Test
    void reportsItselfAsTheSmtpProvider() {
        // The registry selects by this id, so it must match the configured COMMUNICATION_PROVIDER.
        assertThat(gateway(mock(JavaMailSender.class)).provider().value()).isEqualTo("SMTP");
    }

    @Test
    void aSuccessfulSendIsSentAndNotRetryable() {
        MimeMessage message = mock(MimeMessage.class);
        EmailGateway.EmailSendResult result = gateway(senderReturning(message)).send(email());

        assertThat(result.sent()).isTrue();
        assertThat(result.retryable()).isFalse();
        assertThat(result.providerReference()).startsWith("SMTP-");
    }

    @Test
    void aTransportFailureIsRetryable() {
        // Connection refused / relay down: the provider may recover, so the bounded backoff retries.
        JavaMailSender sender = senderReturning(mock(MimeMessage.class));
        doThrow(new MailSendException("Connection refused")).when(sender).send(any(MimeMessage.class));

        EmailGateway.EmailSendResult result = gateway(sender).send(email());

        assertThat(result.sent()).isFalse();
        assertThat(result.retryable()).as("a dead relay must be retried").isTrue();
        assertThat(result.providerReference()).isNull();
    }

    @Test
    void anAuthenticationFailureIsRetryable() {
        JavaMailSender sender = senderReturning(mock(MimeMessage.class));
        doThrow(new MailAuthenticationException("bad credentials")).when(sender).send(any(MimeMessage.class));

        assertThat(gateway(sender).send(email()).retryable()).isTrue();
    }

    @Test
    void aMissingRecipientIsPermanentAndNeverTouchesTheTransport() {
        // Retrying an absent address fails identically every time; it must not consume the budget.
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailGateway.RenderedEmail noRecipient = new EmailGateway.RenderedEmail(null, "Subject", "<p>x</p>");

        EmailGateway.EmailSendResult result = gateway(sender).send(noRecipient);

        assertThat(result.sent()).isFalse();
        assertThat(result.retryable()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(sender);
    }

    @Test
    void queryReturnsEmptyBecauseSmtpHasNoStatusEndpoint() {
        // Fabricating a status would be worse than reporting none (§38).
        assertThat(gateway(mock(JavaMailSender.class)).query("SMTP-anything")).isEmpty();
    }
}