package com.company.exam.communication.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Reusable email content ({@code specs/19-P10-Communication-V1.5.md §7.2, §21}).
 *
 * <p>{@code subject} and {@code body} may contain {@code {{variableName}}} placeholders rendered by
 * pure string replacement ({@code §24}). There is no publication/version column ({@code §22}).
 */
@Entity
@Table(name = "email_template")
public class EmailTemplate extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
