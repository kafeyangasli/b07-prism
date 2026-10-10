package com.github.kafeyangasli.prism.feature.user.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(assignableTypes = AvatarController.class)
public class AvatarExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "Unggahan terlalu besar. Pilih foto profil sesuai batas ukuran.");
        return "redirect:/account";
    }
}
