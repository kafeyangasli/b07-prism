package com.github.kafeyangasli.prism.feature.administration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.github.kafeyangasli.prism.feature.administration.model.ApplicationSetting;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> {
}
