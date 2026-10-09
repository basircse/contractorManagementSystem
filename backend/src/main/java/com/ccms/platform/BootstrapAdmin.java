package com.ccms.platform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first platform admin on an empty database. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Value("${ccms.bootstrap-admin.username}")
    private String username;
    @Value("${ccms.bootstrap-admin.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode(password));
        admin.setFullName("System Admin");
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.warn("Created bootstrap admin user '{}'. Change its password after first login.", username);
    }
}
