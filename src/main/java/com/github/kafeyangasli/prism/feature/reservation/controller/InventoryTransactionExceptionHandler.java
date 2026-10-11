package com.github.kafeyangasli.prism.feature.reservation.controller;

import com.github.kafeyangasli.prism.feature.facility.controller.AdminFacilityController;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(assignableTypes = {ReservationController.class, ReservationProcessingController.class, AdminFacilityController.class})
public class InventoryTransactionExceptionHandler {
    @ExceptionHandler({ConcurrencyFailureException.class, TransactionException.class})
    public String conflict(HttpServletRequest request, RedirectAttributes redirect) {
        String path = request.getRequestURI();
        redirect.addFlashAttribute(path.startsWith("/admin/") ? "errorMessage" : "error",
                "Data sedang diproses oleh permintaan lain. Periksa kembali ketersediaan lalu coba lagi.");
        return "redirect:" + (path.startsWith("/admin/") ? "/admin/facilities"
                : path.startsWith("/staff/") ? "/staff/reservations" : "/reservations/new");
    }
}
