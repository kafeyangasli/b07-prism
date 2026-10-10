package com.github.kafeyangasli.prism.feature.user.dto;

import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;

// Only safe, public account fields enter the rendering model.
public record AccountSettingsView(String name, String email, Role role, AccountStatus accountStatus) { }
