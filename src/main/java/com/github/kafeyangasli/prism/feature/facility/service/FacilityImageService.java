package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
@Transactional
public class FacilityImageService {
    private final FacilityService facilities;
    private final FacilityRepository facilityRepository;
    private final FacilityImageRepository images;
    private final UserRepository users;
    private final FacilityImageStorage storage;
    private final int maxCount;

    public FacilityImageService(FacilityService facilities, FacilityRepository facilityRepository,
            FacilityImageRepository images, UserRepository users, FacilityImageStorage storage,
            @Value("${prism.facility-images.max-count:20}") int maxCount) {
        this.facilities = facilities; this.facilityRepository = facilityRepository;
        this.images = images; this.users = users; this.storage = storage; this.maxCount = maxCount;
    }

    public Facility create(Long adminId, FacilityDto dto, List<MultipartFile> uploads, Integer thumbnailIndex) {
        Facility facility = facilities.createFacility(adminId, dto);
        add(adminId, facility.getId(), uploads, thumbnailIndex);
        return facility;
    }

    public void add(Long adminId, Long facilityId, List<MultipartFile> uploads, Integer thumbnailIndex) {
        Facility facility = lock(adminId, facilityId);
        List<FacilityImage> existing = ordered(facilityId);
        List<MultipartFile> files = uploads == null ? List.of() : uploads;
        // An optional browser input submits one unnamed empty part without a selection.
        if (files.size() == 1 && files.getFirst().isEmpty()
                && (files.getFirst().getOriginalFilename() == null || files.getFirst().getOriginalFilename().isBlank()))
            files = List.of();
        if (existing.size() + files.size() > maxCount) throw new BusinessRuleException("Jumlah gambar melebihi batas " + maxCount + ".");
        if (thumbnailIndex != null && (thumbnailIndex < 0 || thumbnailIndex >= files.size()))
            throw new BusinessRuleException("Pilihan thumbnail tidak valid.");
        int order = existing.stream().mapToInt(FacilityImage::getDisplayOrder).max().orElse(-1) + 1;
        List<FacilityImage> added = new ArrayList<>();
        for (MultipartFile file : files) {
            String name;
            try { name = storeForTransaction(file); }
            catch (BusinessRuleException e) {
                throw new BusinessRuleException("Gambar ke-" + (added.size() + 1) + ": " + e.getMessage());
            }
            FacilityImage image = new FacilityImage(facility, name, order++);
            // Keep both sides current, including the newly created facility rendered by HTMX.
            facility.getImages().add(image);
            added.add(images.save(image));
        }
        if (thumbnailIndex != null) select(existing, added.get(thumbnailIndex));
        else if (!added.isEmpty() && existing.stream().noneMatch(FacilityImage::isThumbnail))
            select(existing, added.getFirst());
    }

    public void selectThumbnail(Long adminId, Long facilityId, Long imageId) {
        lock(adminId, facilityId);
        select(ordered(facilityId), requireImage(facilityId, imageId));
    }

    public void replace(Long adminId, Long facilityId, Long imageId, MultipartFile upload) {
        lock(adminId, facilityId);
        FacilityImage image = requireImage(facilityId, imageId);
        String name = storeForTransaction(upload);
        String old = image.getStoragePath();
        image.setStoragePath(name);
        deleteAfterCommit(old);
    }

    public void delete(Long adminId, Long facilityId, Long imageId) {
        Facility facility = lock(adminId, facilityId);
        FacilityImage image = requireImage(facilityId, imageId);
        boolean thumbnail = image.isThumbnail();
        facility.getImages().remove(image);
        images.delete(image);
        images.flush();
        if (thumbnail) {
            List<FacilityImage> remaining = ordered(facilityId);
            if (!remaining.isEmpty()) select(remaining, remaining.getFirst());
        }
        deleteAfterCommit(image.getStoragePath());
    }

    @Transactional(readOnly = true)
    public List<FacilityImage> ordered(Long facilityId) {
        return images.findByFacilityIdOrderByDisplayOrderAscIdAsc(facilityId);
    }

    @Transactional(readOnly = true)
    public FacilityImage requireImage(Long facilityId, Long imageId) {
        return images.findByIdAndFacilityId(imageId, facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Gambar fasilitas tidak ditemukan."));
    }

    private Facility lock(Long adminId, Long facilityId) {
        if (adminId == null || users.findById(adminId).filter(u -> u.getRole() == Role.ADMIN).isEmpty())
            throw new BusinessRuleException("Tindakan ini hanya dapat dilakukan oleh Admin.");
        return facilityRepository.findByIdForUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas tidak ditemukan."));
    }

    private void select(List<FacilityImage> existing, FacilityImage target) {
        existing.forEach(image -> image.setThumbnail(false));
        // Release the old unique key before assigning the new one.
        images.flush();
        target.setThumbnail(true);
        images.flush();
    }

    private String storeForTransaction(MultipartFile upload) {
        return storage.storeForTransaction(upload);
    }

    private void deleteAfterCommit(String name) {
        storage.deleteAfterCommit(name);
    }
}
