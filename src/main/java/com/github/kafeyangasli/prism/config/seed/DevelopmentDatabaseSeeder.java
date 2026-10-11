package com.github.kafeyangasli.prism.config.seed;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityImage;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityImageRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityImageStorage;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Profile("!prod & !production")
@ConditionalOnProperty(name = "prism.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevelopmentDatabaseSeeder implements ApplicationRunner {
    private final FacilityRepository facilities;
    private final FacilityTypeRepository types;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final Path directory;
    private final FacilityImageRepository images;
    private final FacilityImageStorage imageStorage;
    private final int maxImageCount;

    public DevelopmentDatabaseSeeder(FacilityRepository facilities, FacilityTypeRepository types,
                                     UserRepository users, PasswordEncoder passwords, Clock clock,
                                     @Value("${prism.seed.directory:./initial_data}") String directory,
                                     FacilityImageRepository images, FacilityImageStorage imageStorage,
                                     @Value("${prism.facility-images.max-count:20}") int maxImageCount) {
        this.facilities = facilities;
        this.types = types;
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
        this.directory = Path.of(directory);
        this.images = images;
        this.imageStorage = imageStorage;
        this.maxImageCount = maxImageCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws IOException {
        // Parse/validate every source before writing; any later conflict rolls back the entire seed.
        var entries = FacilitySeedSource.read(directory);
        for (var entry : entries) {
            Facility facility = facilities.findByCodeIgnoreCase(entry.code()).orElse(null);
            if (facility == null) {
                FacilityType type = types.findByCodeIgnoreCase(entry.typeCode())
                        .or(() -> types.findByNameIgnoreCase(entry.typeName()))
                        .orElseGet(() -> types.save(new FacilityType(entry.typeCode(), entry.typeName(), null)));
                if (type.isQuantityBased() != (entry.stock() != null)) {
                    throw new IllegalStateException("Facility seed " + entry.code() + ": incompatible existing type " + type.getCode());
                }
                facility = new Facility(entry.code(), entry.name(), type, entry.location(),
                        entry.capacity(), entry.description(), AdministrativeStatus.ACTIVE);
                facility.setTotalStock(entry.stock());
                facilities.save(facility);
            }
            seedImage(entry, facility);
        }
        User admin = account("Admin", "admin@university.ac.id", "admin_prism", Role.ADMIN, null);
        account("Pengguna 1", "pengguna1@university.ac.id", "pengguna_prism", Role.PENGGUNA, admin);
        account("Pengguna 2", "pengguna2@university.ac.id", "pengguna_prism", Role.PENGGUNA, admin);
        account("Petugas", "petugas@university.ac.id", "petugas_prism", Role.PETUGAS, admin);
    }

    private void seedImage(FacilitySeedSource.Entry entry, Facility facility) throws IOException {
        if (entry.image() == null) return;
        // Share the gallery's facility lock, so an upload and a startup cannot both add a first image.
        facility = facilities.findByIdForUpdate(facility.getId()).orElseThrow();
        if (!images.findByFacilityIdOrderByDisplayOrderAscIdAsc(facility.getId()).isEmpty()) return;
        if (maxImageCount < 1) throw new IllegalStateException("Facility seed " + entry.code() + ": image limit must allow at least one image");
        String storagePath;
        try {
            storagePath = imageStorage.storeForTransaction(new SeedImageFile(entry.image(), Files.size(entry.image())));
        } catch (RuntimeException | IOException ex) {
            throw new IllegalStateException("Cannot seed image " + entry.image() + " for facility " + entry.code(), ex);
        }
        FacilityImage image = new FacilityImage(facility, storagePath, 0);
        image.setThumbnail(true);
        facility.getImages().add(image);
        images.save(image);
    }

    private User account(String name, String email, String password, Role role, User verifier) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User(name, email, passwords.encode(password), role, AccountStatus.ACTIVE);
            user.setVerifiedAt(LocalDateTime.now(clock));
            // Bootstrap admin has a verification timestamp but no preceding human verifier.
            user.setVerifiedBy(verifier);
            return users.save(user);
        });
    }
}
