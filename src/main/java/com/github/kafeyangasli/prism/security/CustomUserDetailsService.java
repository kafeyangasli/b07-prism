package com.github.kafeyangasli.prism.security;

import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Pengguna dengan email " + email + " tidak ditemukan."));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new DisabledException("Akun belum aktif atau tidak dapat digunakan.");
        }

        return new PrismUserDetails(user);
    }
}
