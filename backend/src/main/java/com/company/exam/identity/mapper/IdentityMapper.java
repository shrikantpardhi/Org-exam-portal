package com.company.exam.identity.mapper;

import com.company.exam.identity.dto.PermissionDtos;
import com.company.exam.identity.dto.RoleDtos;
import com.company.exam.identity.dto.SessionDtos;
import com.company.exam.identity.dto.UserDtos;
import com.company.exam.identity.entity.LoginSession;
import com.company.exam.identity.entity.Permission;
import com.company.exam.identity.entity.Role;
import com.company.exam.identity.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Entity-to-DTO mapping ({@code specs/10-P1-Identity-V1.5.md §112}).
 *
 * <p>Keeps the entity/DTO boundary in one place so controllers and services never expose JPA
 * entities. Roles are passed in explicitly from an already-resolved projection to avoid lazy
 * association traversal.
 */
@Component
public class IdentityMapper {

    public UserDtos.UserSummary toSummary(User user, List<String> roles) {
        return new UserDtos.UserSummary(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getMobile(), user.getStatus(), List.copyOf(roles));
    }

    public UserDtos.UserDetail toDetail(User user, List<String> roles) {
        return new UserDtos.UserDetail(user.getId(), user.getOrganizationId(), user.getFirstName(),
                user.getLastName(), user.getEmail(), user.getMobile(), user.getStatus(),
                List.copyOf(roles), user.getCreatedOn(), user.getUpdatedOn());
    }

    public RoleDtos.RoleSummary toSummary(Role role) {
        return new RoleDtos.RoleSummary(role.getId(), role.getCode(), role.getName(),
                role.getDescription(), role.getStatus());
    }

    public PermissionDtos.PermissionSummary toSummary(Permission permission) {
        return new PermissionDtos.PermissionSummary(permission.getId(), permission.getCode(),
                permission.getName(), permission.getDescription(), permission.getStatus());
    }

    public SessionDtos.LoginSessionSummary toSummary(LoginSession session) {
        return new SessionDtos.LoginSessionSummary(session.getId(), session.getUserId(),
                session.getLoginTime(), session.getLogoutTime(), session.getSessionStatus(),
                session.getIpAddress(), session.getDeviceInfo());
    }

    public SessionDtos.LoginSessionDetail toDetail(LoginSession session) {
        return new SessionDtos.LoginSessionDetail(session.getId(), session.getUserId(),
                session.getLoginTime(), session.getLogoutTime(), session.getSessionStatus(),
                session.getIpAddress(), session.getDeviceInfo());
    }
}
