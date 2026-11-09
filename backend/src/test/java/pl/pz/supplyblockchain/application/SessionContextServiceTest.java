package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.session.SessionContext;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.AuthSession;
import pl.pz.supplyblockchain.domain.Role;

class SessionContextServiceTest {

    private final AuthService authService = mock(AuthService.class);
    private final RoleService roleService = mock(RoleService.class);
    private final ChainProperties chainProperties = new ChainProperties(
            31337, "0x5fbdb2315678afecb367f032d93f642f64180aa3", "SupplyChain", "1", 3600);
    private final SessionContextService service =
            new SessionContextService(authService, roleService, chainProperties);
    private final String address = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

    @Test
    void currentReadsRolesFromTheRegistryOnEveryCall() {
        AuthSession session = new AuthSession(
                UUID.randomUUID(), "hash", address, 31337,
                Instant.ofEpochSecond(1800000000L), Instant.ofEpochSecond(1800028800L), 1800,
                Instant.ofEpochSecond(1800000000L), null);
        when(authService.authenticateSession("Bearer token")).thenReturn(session);
        when(roleService.rolesOf(address)).thenReturn(
                EnumSet.of(Role.OPERATOR_ROLE, Role.QA_INSPECTOR_ROLE));

        SessionContext context = service.current("Bearer token");

        assertThat(context.address()).isEqualTo(address);
        assertThat(context.roles()).containsExactly(Role.OPERATOR_ROLE, Role.QA_INSPECTOR_ROLE);
        assertThat(context.expiresAt()).isEqualTo(1800028800L);
        assertThat(context.chainId()).isEqualTo(31337);
        assertThat(context.verifyingContract())
                .isEqualTo("0x5fbdb2315678afecb367f032d93f642f64180aa3");
    }
}
