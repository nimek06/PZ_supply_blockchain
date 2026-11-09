package pl.pz.supplyblockchain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.pz.supplyblockchain.application.ports.RoleRegistry;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.Role;

class RoleServiceTest {

    private final RoleRegistry registry = mock(RoleRegistry.class);
    private final RoleService service = new RoleService(registry);
    private final String address = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

    @Test
    void requireOperatorAcceptsCurrentRole() {
        when(registry.rolesOf(address)).thenReturn(Set.of(Role.OPERATOR_ROLE));

        service.requireOperator(address);
    }

    @Test
    void requireOperatorRejectsMissingRole() {
        when(registry.rolesOf(address)).thenReturn(Set.of(Role.QA_INSPECTOR_ROLE));

        assertThatThrownBy(() -> service.requireOperator(address))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.ROLE_MISSING));
    }

    @Test
    void adminDoesNotImplyBusinessRole() {
        when(registry.rolesOf(address)).thenReturn(Set.of(Role.DEFAULT_ADMIN_ROLE));

        assertThat(service.isAdmin(address)).isTrue();
        assertThatThrownBy(() -> service.requireOperator(address))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.ROLE_MISSING));
    }

    @Test
    void qaInspectorRoleIsCheckedSeparately() {
        when(registry.rolesOf(address)).thenReturn(Set.of(Role.QA_INSPECTOR_ROLE));

        service.requireQaInspector(address);
    }
}
