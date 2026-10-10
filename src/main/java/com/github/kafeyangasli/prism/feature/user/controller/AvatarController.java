package com.github.kafeyangasli.prism.feature.user.controller;

import com.github.kafeyangasli.prism.feature.user.service.AccountSettingsService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;

@Controller
@RequestMapping("/account/avatar")
public class AvatarController {
    private final AccountSettingsService accounts;

    public AvatarController(AccountSettingsService accounts) { this.accounts = accounts; }

    @GetMapping
    @ResponseBody
    public ResponseEntity<byte[]> image() throws IOException {
        byte[] bytes;
        try { bytes = accounts.currentAvatar(); }
        catch (ResourceNotFoundException missingFile) { bytes = null; }
        MediaType type = MediaType.IMAGE_PNG;
        if (bytes == null) {
            try (var input = new ClassPathResource("static/images/avatar-placeholder.svg").getInputStream()) {
                bytes = input.readAllBytes();
            }
            type = MediaType.valueOf("image/svg+xml");
        }
        // Owner-only route, with no filename/ID URL and no persistent browser cache.
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(bytes);
    }

    @PostMapping
    public String upload(@RequestParam(required = false) MultipartFile image, RedirectAttributes redirect) {
        return change(() -> accounts.updateAvatar(image), "Foto profil berhasil diperbarui.", redirect);
    }

    @PostMapping("/remove")
    public String remove(RedirectAttributes redirect) {
        return change(accounts::removeAvatar, "Foto profil berhasil dihapus.", redirect);
    }

    private String change(Runnable operation, String success, RedirectAttributes redirect) {
        try {
            operation.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (BusinessRuleException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        } catch (DataAccessException | TransactionException | IllegalStateException exception) {
            redirect.addFlashAttribute("errorMessage", "Foto profil belum dapat disimpan. Coba lagi nanti.");
        }
        return "redirect:/account";
    }
}
