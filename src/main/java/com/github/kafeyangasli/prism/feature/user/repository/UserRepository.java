package com.github.kafeyangasli.prism.feature.user.repository;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;

import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // Authentication and self-registration (email is normalized by the entity callback).
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    // Admin user management and registration verification.
    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    List<User> findByAccountStatusOrderByCreatedAtAsc(AccountStatus accountStatus);

    List<User> findByRoleAndAccountStatusOrderByCreatedAtDesc(Role role, AccountStatus accountStatus);

    List<User> findByAccountStatusAndVerifiedAtIsNullOrderByCreatedAtAsc(AccountStatus accountStatus);

    // Approval lock order: facility first, then the requesting user.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
