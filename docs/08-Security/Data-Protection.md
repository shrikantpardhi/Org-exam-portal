# Data Protection

**Document Version:** 1.0  
**Status:** Approved  
**Parent Document:** Security Architecture

---

# 1. Purpose

This document defines the data-protection requirements for the Configurable Examination Platform.

The platform processes several categories of sensitive information, including:

- User identity information
- Organization information
- Candidate information
- Examination content
- Candidate responses
- Evaluation records
- Results
- Audit records
- Authentication information
- Integration credentials

The objective is to ensure that data remains:

- Confidential
- Accurate
- Available
- Traceable
- Appropriately retained
- Protected throughout its lifecycle

---

# 2. Scope

This policy applies to data:

- Created by users
- Received through APIs
- Stored in databases
- Stored in caches
- Transmitted between services
- Displayed through the UI
- Exported through reports
- Included in backups
- Archived
- Deleted

It applies across:

```text
Development
Testing
Staging
Production
Backup
Archive
```

---

# 3. Data Protection Principles

## 3.1 Data Minimization

The platform should collect and retain only the information necessary to provide the intended functionality.

Do not collect personal information merely because it may be useful later.

---

## 3.2 Purpose Limitation

Data should only be used for the business purpose for which it was collected or for an explicitly authorized purpose.

---

## 3.3 Confidentiality

Sensitive data must only be accessible to authorized users and systems.

---

## 3.4 Integrity

Business-critical data must not be modified without authorization.

This is particularly important for:

- Published questions
- Published assessments
- Candidate responses
- Evaluation records
- Results
- Audit records

---

## 3.5 Availability

Required data must remain available to authorized users and examination processes.

Availability must be balanced with security and operational controls.

---

## 3.6 Traceability

Significant changes to sensitive data must be attributable to an actor or system process.

---

# 4. Data Classification

The platform should classify information according to sensitivity.

## Public

Information that can safely be exposed publicly.

Examples:

- Public organization information
- Public assessment descriptions
- Public documentation

---

## Internal

Information intended for authenticated platform users.

Examples:

- General configuration
- Internal academic metadata
- Operational information

---

## Confidential

Information that should only be accessible to authorized users.

Examples:

- Candidate profiles
- Internal question banks
- Assessment configurations
- Evaluation records

---

## Highly Confidential

Information requiring strict access controls.

Examples:

- Password hashes
- Authentication secrets
- Examination answer keys before release
- Candidate responses during restricted examination periods
- Published-but-restricted results
- Integration credentials
- Security audit information

---

# 5. Data Classification Table

| Data | Classification | Primary Controls |
|---|---|---|
| User profile | Confidential | RBAC, organization isolation |
| Password hash | Highly Confidential | Strong hashing, restricted access |
| Access token | Highly Confidential | Secure transport, short lifetime |
| Refresh token | Highly Confidential | Secure storage, rotation, revocation |
| Question draft | Confidential | RBAC, organization scope |
| Published question | Confidential | Versioning, access control |
| Answer key | Highly Confidential | Strict authorization |
| Assessment configuration | Confidential | RBAC, versioning |
| Candidate response | Highly Confidential | Resource authorization |
| Evaluation | Highly Confidential | RBAC, audit |
| Result | Highly Confidential | Publication state, RBAC |
| Audit event | Highly Confidential | Restricted access, immutability |
| Application logs | Internal/Confidential | Access control, redaction |
| Database backup | Highly Confidential | Encryption, restricted access |

---

# 6. Personal Data

The platform may process personal information such as:

- Name
- Email address
- Phone number
- User identifier
- Organization membership
- Examination participation
- Scores
- Results
- Evaluation information
- Audit-related identity information

The exact data collected must be defined by the PRD and domain model.

---

# 7. Sensitive Examination Data

Examination content requires additional protection.

Sensitive examination information includes:

- Unpublished questions
- Question explanations
- Answer options
- Answer keys
- Blueprint configuration
- Assessment composition
- Candidate responses
- Evaluation information
- Examination results before authorized publication

Access must be restricted according to role and lifecycle state.

---

# 8. Answer-Key Protection

Answer keys must be treated as highly sensitive.

Before the permitted release point:

```text
Student
  |
  X
Answer Key
```

Even if a student can access an assessment, the API must not expose answer-key information.

Answer keys should only be accessible to authorized services and users.

---

# 9. Published Content Immutability

Publishing an examination artifact creates a historical boundary.

Published:

- Questions
- Blueprints
- Assessments
- Answer keys

must not be silently modified.

If a change is required:

```text
Published Version
       |
       X Modify
       |
       v
New Version
```

This preserves examination reproducibility.

---

# 10. Candidate Data Isolation

Candidate data must be protected by:

```text
Authentication
+
Authorization
+
Organization Scope
+
Resource Scope
```

A valid user account must not automatically grant access to all candidate information.

---

# 11. Student Data Access

Students should only be able to access information belonging to themselves unless an explicit business rule permits broader access.

Examples:

```text
Own Profile        -> Allowed
Own Attempt        -> Allowed
Own Responses      -> Allowed where appropriate
Own Published Result -> Allowed
Other Student Result -> Denied
Other Student Response -> Denied
```

---

# 12. Administrative Access

Administrative users may require access to sensitive data, but administrative access must follow least privilege.

The platform should distinguish between:

- User administration
- Academic administration
- Examination administration
- Evaluation administration
- Security administration

An administrator should not receive unrestricted access merely because they have an administrative role.

---

# 13. Data in Transit

Sensitive data must be protected during transmission.

Production communication must use HTTPS/TLS.

This applies to:

- Browser → API
- Mobile/PWA → API
- API → external services
- Administration tools → API

Plain HTTP must not be used for authenticated production traffic.

---

# 14. Internal Service Communication

Although Version 1 uses a modular monolith, internal module communication must still follow security boundaries.

Modules should not bypass business services to access another module's sensitive data directly.

For example:

```text
Delivery Module
      |
      v
Approved Service Interface
      |
      v
Question Module
```

rather than unrestricted direct access to internal persistence structures.

---

# 15. Data at Rest

Sensitive production data should be protected at rest through appropriate infrastructure and database security controls.

Controls may include:

- Encrypted storage
- Database access controls
- Encrypted backups
- Restricted administrative access
- Secret management

Encryption implementation depends on the deployment environment.

---

# 16. Database Security

Database access must follow least privilege.

Application users should receive only the database privileges required by the application.

Database administrative privileges must not be used by normal application processes.

Database credentials must never be committed to source control.

---

# 17. Secrets

Secrets include:

- JWT signing keys
- Database passwords
- Redis credentials
- SMTP credentials
- Payment credentials
- External API keys
- Encryption keys

Secrets must be managed outside application source code.

Preferred mechanisms include:

- Environment-based secret injection
- Secret-management systems
- Protected CI/CD variables

---

# 18. Cache Protection

Redis or other caching infrastructure must not become an uncontrolled copy of sensitive data.

Sensitive cached information must:

- Have appropriate expiration.
- Be protected from unauthorized access.
- Avoid unnecessary persistence.
- Be invalidated when underlying authorization changes.

Do not cache highly sensitive data merely for convenience.

---

# 19. Logging Data

Application logs must be sanitized.

The following must never appear in normal logs:

- Passwords
- Access tokens
- Refresh tokens
- JWT signing keys
- Database credentials
- API secrets
- Full payment credentials

Candidate responses and examination content should not be logged unless specifically required for an approved operational purpose.

---

# 20. Audit Data

Audit records may contain sensitive information and must therefore receive strong access controls.

Audit records must:

- Be immutable.
- Be protected from unauthorized access.
- Avoid credentials and secrets.
- Follow retention requirements.

Detailed requirements are defined in `Audit-Logging.md`.

---

# 21. API Response Protection

APIs must return only the data required by the client.

The backend must not expose internal fields simply because they exist in the database entity.

For example, a user response should not automatically expose:

```text
passwordHash
securityFlags
internalNotes
authenticationMetadata
```

Use explicit response DTOs.

---

# 22. Input Protection

User-supplied data must be validated before processing.

Controls include:

- Schema validation
- Type validation
- Length limits
- Allowed-value validation
- Business validation
- File validation
- Content sanitization where applicable

---

# 23. Rich Text and HTML

Questions and explanations may contain rich content.

Rich content must be treated as untrusted input.

The platform must protect against:

- Cross-site scripting
- Malicious HTML
- Script injection
- Unsafe URLs
- Dangerous embedded content

Content sanitization should occur according to the approved frontend/backend architecture.

---

# 24. File Uploads

If the platform supports images or other uploaded assets, uploaded files must be validated.

Controls should include:

- File-size limits
- Allowed MIME types
- Extension validation
- Content validation
- Malware scanning where appropriate
- Secure storage
- Access control
- Safe file naming

User-provided filenames must not become trusted filesystem paths.

---

# 25. Export Protection

Exports may contain large amounts of sensitive information.

Examples:

- Candidate lists
- Results
- Question banks
- Assessment reports
- Evaluation reports

Export functionality must require appropriate permissions.

Exports should:

- Be auditable.
- Be generated securely.
- Use controlled formats.
- Avoid unnecessary fields.
- Have appropriate retention.

---

# 26. Download Security

Sensitive files should not be exposed through predictable public URLs.

Where protected downloads are required, use:

- Authorization checks
- Short-lived signed URLs where appropriate
- Authenticated download endpoints
- Access logging

---

# 27. Data Masking

Sensitive information should be masked when full values are unnecessary.

Examples:

```text
Email:
sh****@example.com

Phone:
******1234
```

Masking may be used in:

- Administrative screens
- Logs
- Support interfaces
- Reports

The masking strategy must not prevent legitimate authorized