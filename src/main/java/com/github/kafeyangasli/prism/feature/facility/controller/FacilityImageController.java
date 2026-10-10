package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.service.*;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.shared.exception.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
public class FacilityImageController {
    private final FacilityImageService images;
    private final FacilityImageStorage storage;
    private final FacilityService facilities;
    private final UserService users;

    public FacilityImageController(FacilityImageService images, FacilityImageStorage storage,
                                   FacilityService facilities, UserService users) {
        this.images = images; this.storage = storage; this.facilities = facilities; this.users = users;
    }

    @GetMapping("/facilities/{facilityId}/images/{imageId}")
    @ResponseBody
    public ResponseEntity<byte[]> image(@PathVariable Long facilityId, @PathVariable Long imageId) {
        try {
            String name = images.requireImage(facilityId, imageId).getStoragePath();
            byte[] bytes;
            try { bytes = storage.load(name); }
            catch (ResourceNotFoundException missingFile) {
                // The record exists but storage may be temporarily unavailable. Also works without JS.
                try (var placeholder = new org.springframework.core.io.ClassPathResource("static/images/facility-placeholder.svg").getInputStream()) {
                    return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml"))
                            .cacheControl(CacheControl.noStore()).body(placeholder.readAllBytes());
                } catch (java.io.IOException e) { return ResponseEntity.notFound().build(); }
            }
            return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                    .cacheControl(CacheControl.noCache())
                    .contentType(name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                    .body(bytes);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/admin/facilities/{facilityId}/images")
    public String add(@PathVariable Long facilityId, @RequestParam(required = false) List<MultipartFile> images,
                      @RequestParam(required = false) Integer thumbnailIndex, Authentication authentication,
                      RedirectAttributes redirect, Model model,
                      @RequestHeader(value = "HX-Request", required = false) String hx) {
        return perform(facilityId, redirect, model, hx,
                () -> this.images.add(adminId(authentication), facilityId, images, thumbnailIndex));
    }

    @PostMapping("/admin/facilities/{facilityId}/images/{imageId}/thumbnail")
    public String thumbnail(@PathVariable Long facilityId, @PathVariable Long imageId, Authentication authentication,
                            RedirectAttributes redirect, Model model,
                            @RequestHeader(value = "HX-Request", required = false) String hx) {
        return perform(facilityId, redirect, model, hx,
                () -> images.selectThumbnail(adminId(authentication), facilityId, imageId));
    }

    @PostMapping("/admin/facilities/{facilityId}/images/{imageId}/delete")
    public String delete(@PathVariable Long facilityId, @PathVariable Long imageId, Authentication authentication,
                         RedirectAttributes redirect, Model model,
                         @RequestHeader(value = "HX-Request", required = false) String hx) {
        return perform(facilityId, redirect, model, hx,
                () -> images.delete(adminId(authentication), facilityId, imageId));
    }

    @PostMapping("/admin/facilities/{facilityId}/images/{imageId}/replace")
    public String replace(@PathVariable Long facilityId, @PathVariable Long imageId, @RequestParam MultipartFile image,
                          Authentication authentication, RedirectAttributes redirect, Model model,
                          @RequestHeader(value = "HX-Request", required = false) String hx) {
        return perform(facilityId, redirect, model, hx,
                () -> images.replace(adminId(authentication), facilityId, imageId, image));
    }

    private Long adminId(Authentication authentication) { return users.findByEmail(authentication.getName()).getId(); }

    private String perform(Long facilityId, RedirectAttributes redirect, Model model, String hx, Runnable operation) {
        String error = null;
        try { operation.run(); }
        catch (BusinessRuleException | ResourceNotFoundException e) { error = e.getMessage(); }
        if ("true".equalsIgnoreCase(hx)) {
            model.addAttribute("f", facilities.getFacilityById(facilityId));
            // Query afresh after a mutation; the request's existing lazy collection may be stale.
            model.addAttribute("galleryImages", images.ordered(facilityId));
            model.addAttribute(error == null ? "imageSuccess" : "imageError",
                    error == null ? "Galeri berhasil diperbarui." : error);
            return "admin/facility-images :: manager";
        }
        redirect.addFlashAttribute(error == null ? "successMessage" : "errorMessage",
                error == null ? "Galeri berhasil diperbarui." : error);
        return "redirect:/admin/facilities";
    }
}
