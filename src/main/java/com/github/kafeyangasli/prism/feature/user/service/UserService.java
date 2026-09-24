package com.github.kafeyangasli.prism.feature.user.service;

import com.github.kafeyangasli.prism.feature.user.dto.AdminCreateUserDto;
import com.github.kafeyangasli.prism.feature.user.dto.UserRegistrationDto;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;

import java.util.List;

public interface UserService {
    User registerUser(UserRegistrationDto dto);

    User verifyUser(Long adminId, Long userId);

    User rejectUser(Long adminId, Long userId);

    User createUserByAdmin(Long adminId, AdminCreateUserDto dto);

    User deactivateUser(Long adminId, Long userId);

    User activateUser(Long adminId, Long userId);

    List<User> findUsersByStatus(AccountStatus status);

    List<User> findAllUsers();

    List<User> findUsers(String search, Role role, AccountStatus status, String sort, String direction);

    User findById(Long id);

    User findByEmail(String email);
}
