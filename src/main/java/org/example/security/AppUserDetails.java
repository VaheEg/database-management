package org.example.security;

import lombok.Getter;
import org.example.entity.User;
import org.example.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts a {@link User} to Spring Security. Carries the user's id so downstream
 * services can resolve the caller without another lookup by username.
 *
 * <p>Only application {@code ROLES} become authorities here. Database-scoped
 * permissions ({@code ADDITIONAL_ROLES}) are deliberately not authorities: they
 * apply to one {@code DbConnectionDetails} at a time and are checked per request.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<GrantedAuthority> authorities;

    public AppUserDetails(Long id, String username, String password, boolean enabled,
                          Collection<GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = List.copyOf(authorities);
    }

    /** Requires {@code userRoles} (and their roles) to already be loaded. */
    public static AppUserDetails from(User user) {
        List<GrantedAuthority> authorities = user.getUserRoles().stream()
                .map(UserRole::getRole)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(authorityOf(role.getName().name())))
                .toList();
        return new AppUserDetails(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(), authorities);
    }

    public static String authorityOf(String roleName) {
        return roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
