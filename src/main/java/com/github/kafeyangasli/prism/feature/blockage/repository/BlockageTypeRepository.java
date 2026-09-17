package com.github.kafeyangasli.prism.feature.blockage.repository;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockageTypeRepository extends JpaRepository<BlockageType, Long> {

    // Admin manages types by deactivating referenced types instead of deleting them.
    Optional<BlockageType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<BlockageType> findByActiveTrueOrderByNameAsc();

    List<BlockageType> findByNameContainingIgnoreCaseAndActiveTrueOrderByNameAsc(String name);
}
