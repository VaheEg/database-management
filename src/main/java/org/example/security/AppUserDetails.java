package org.example.security;

import lombok.Getter;
import org.example.entity.User;
import org.example.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

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
