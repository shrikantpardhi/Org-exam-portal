-- ---------------------------------------------------------------------------
-- V31 - Include the reset link in the PASSWORD_RESET email template (ADR-0034)
--
-- ADR-0034 wires password-reset delivery to the Communication email pipeline
-- (closing GAP-002). The V21 seed template told the recipient a reset was
-- requested but carried no link, so a reset token could be delivered but never
-- used. This migration adds the {{resetLink}} placeholder; the delivery service
-- supplies the actual link built from the trusted frontend origin (specs/10-P1
-- §144). The link is a data placeholder, never a hard-coded URL.
--
-- Idempotent: a plain UPDATE keyed on the unique template code.
-- ---------------------------------------------------------------------------

UPDATE email_template
SET body = '<p>Hello {{studentName}}, a password reset was requested for your account. '
        || 'Click the link below to choose a new password.</p>'
        || '<p><a href="{{resetLink}}">Reset your password</a></p>'
        || '<p>If this was not you, ignore this message.</p>',
    updated_by = '00000000-0000-0000-0000-000000000001',
    updated_on = now()
WHERE code = 'PASSWORD_RESET';
