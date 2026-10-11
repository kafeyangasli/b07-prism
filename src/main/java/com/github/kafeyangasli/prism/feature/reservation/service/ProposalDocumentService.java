package com.github.kafeyangasli.prism.feature.reservation.service;

import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.MalformedURLException;

@Service
public class ProposalDocumentService {
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final ProposalStorageService storage;

    public ProposalDocumentService(ReservationRepository reservations, UserRepository users,
                                   ProposalStorageService storage) {
        this.reservations = reservations;
        this.users = users;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PENGGUNA','PETUGAS','ADMIN')")
    public Resource validatedProposal(long id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var actor = users.findByEmailIgnoreCase(email).orElseThrow(this::missing);
        if (actor.getAccountStatus() != AccountStatus.ACTIVE) throw missing();
        var reservation = (actor.getRole() == Role.PETUGAS || actor.getRole() == Role.ADMIN
                ? reservations.findById(id) : reservations.findByIdAndUserId(id, actor.getId()))
                .orElseThrow(this::missing);
        if (!reservation.isProposalValidated() || !reservation.hasValidatedProposal()) throw missing();
        try {
            return storage.load(reservation.getValidatedProposalPath());
        } catch (MalformedURLException | IllegalArgumentException exception) {
            throw missing();
        }
    }

    private ResourceNotFoundException missing() {
        return new ResourceNotFoundException("PDF proposal tervalidasi tidak ditemukan");
    }
}
