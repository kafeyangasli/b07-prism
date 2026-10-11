package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:admin-deactivation;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AdminDeactivationConcurrencyIntegrationTest {
    @Autowired UserRepository users;
    @Autowired UserService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @BeforeEach void clear() { users.deleteAll(); }

    @Test void finalActiveAdminCannotDeactivateItselfEvenWithInactiveAdmins() {
        var active = admin("active", AccountStatus.ACTIVE);
        admin("inactive", AccountStatus.INACTIVE);
        assertThatThrownBy(() -> service.deactivateUser(active.getId(), active.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("satu Admin aktif");
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test void lastAdminProtectionIsEnforcedByAdministrativeEndpoint() throws Exception {
        var active = admin("endpoint-admin", AccountStatus.ACTIVE);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/users/" + active.getId() + "/deactivate")
                        .with(com.github.kafeyangasli.prism.support.PrismTestUsers.user(active.getEmail()).roles("ADMIN"))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/admin/users"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("errorMessage"));
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test void finalAdminProtectionAlsoAppliesWhenTargetDiffersFromActor() {
        var actor = admin("inactive-actor", AccountStatus.INACTIVE);
        var target = admin("last-active", AccountStatus.ACTIVE);
        // An in-flight operation can carry an actor ID whose account was just deactivated.
        assertThatThrownBy(() -> service.deactivateUser(actor.getId(), target.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("satu Admin aktif");
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test void otherAdminCanBeDeactivatedWhenAnActiveAdminRemains() {
        var actor = admin("actor", AccountStatus.ACTIVE);
        var target = admin("target", AccountStatus.ACTIVE);
        service.deactivateUser(actor.getId(), target.getId());
        assertThat(users.findById(target.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void concurrentSelfOrCrossDeactivationsCannotRemoveBothAdmins(boolean crossTarget) throws Exception {
        var first = admin("first", AccountStatus.ACTIVE);
        var second = admin("second", AccountStatus.ACTIVE);
        var beforeLocks = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> deactivate(first.getId(), crossTarget ? second.getId() : first.getId(), beforeLocks));
            var b = executor.submit(() -> deactivate(second.getId(), crossTarget ? first.getId() : second.getId(), beforeLocks));
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(activeAdmins()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private boolean deactivate(Long actor, Long target, CyclicBarrier beforeLocks) {
        var tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                users.findById(actor).orElseThrow(); // Deliberately preload managed state before the other writer commits.
                try { beforeLocks.await(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                service.deactivateUser(actor, target);
                return true;
            }));
        } catch (BusinessRuleException expected) { return false; }
    }
    private User admin(String name, AccountStatus status) {
        return users.saveAndFlush(new User(name, name + "@admin.test", "hash", Role.ADMIN, status));
    }
    private int activeAdmins() { return users.findByRoleAndAccountStatusOrderByCreatedAtDesc(Role.ADMIN, AccountStatus.ACTIVE).size(); }
}
