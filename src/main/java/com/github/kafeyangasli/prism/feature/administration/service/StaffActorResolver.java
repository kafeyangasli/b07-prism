package com.github.kafeyangasli.prism.feature.administration.service;

import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffActorResolver {

    private final UserRepository userRepository;

    public StaffActorResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public long resolveId(String email) {
        User actor = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Akun staf tidak ditemukan"));
        if (actor.getRole() != Role.PETUGAS && actor.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("STAFF_ROLE_REQUIRED",
                    "Operasi ini hanya dapat dilakukan Petugas atau Admin");
        }
        return actor.getId();
    }
}
