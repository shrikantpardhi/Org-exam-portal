package com.company.exam.identity.entity;

import com.company.exam.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Every authenticated platform user ({@code specs/10-P1-Identity-V1.5.md §8}).
 *
 * <p>{@code user} is a reserved PostgreSQL keyword, so the physical table is quoted. Foreign keys
 * are modelled as raw UUID columns rather than JPA associations to keep authorization queries
 * projection-based and free of lazy graph traversal ({@code §70, §135}).
 *
 * <p>The {@code password} column stores only a secure password hash.
 */
@Entity
@Table(name = "\"user\"")
public class User extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
