package com.github.kafeyangasli.prism.feature.user.repository;

import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    interface AuthenticationState {
        AccountStatus getAccountStatus();
        String getPasswordHash();
    }

    // Scalar, uncached DB state: do not trust the session or a managed User snapshot.
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(
            name = org.hibernate.jpa.HibernateHints.HINT_CACHEABLE, value = "false"))
    @Query("select u.accountStatus as accountStatus, u.passwordHash as passwordHash from User u where u.id = :id")
    Optional<AuthenticationState> findAuthenticationStateById(@Param("id") Long id);

    @Query("select u.role from User u where u.id = :id")
    Optional<Role> findRoleById(@Param("id") Long id);

    // Routing IDs only. Admin roles are immutable through account-management endpoints.
    @Query("select u.id from User u where u.role = :role order by u.id")
    List<Long> findIdsByRole(@Param("role") Role role);


    @Query("select u.id from User u where lower(u.email) = lower(:email)")
    Optional<Long> findIdByEmail(@Param("email") String email);

    // Authentication and self-registration (email is normalized by the entity callback).
    Optional<User> findByEmailIgnoreCase(String email);

    // Same controlling user-row lock as approval/admin management. No facility is
    // acquired after this lock. Resolve directly to avoid a pre-lock snapshot.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByIdAndAccountStatus(Long id, AccountStatus accountStatus);

    boolean existsByEmailIgnoreCase(String email);

    // Admin user management and registration verification.
    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    List<User> findByAccountStatusOrderByCreatedAtAsc(AccountStatus accountStatus);

    List<User> findByRoleAndAccountStatusOrderByCreatedAtDesc(Role role, AccountStatus accountStatus);

    List<User> findByAccountStatusAndVerifiedAtIsNullOrderByCreatedAtAsc(AccountStatus accountStatus);

    @Query("""
            select u from User u
            where (:search is null
                   or lower(u.name) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%')))
              and (:role is null or u.role = :role)
              and (:status is null or u.accountStatus = :status)
            """)
    List<User> findForManagement(@Param("search") String search,
                                 @Param("role") Role role,
                                 @Param("status") AccountStatus status,
                                 Sort sort);

    // Approval lock order: facility first, then the requesting user.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
