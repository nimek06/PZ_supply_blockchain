package pl.pz.supplyblockchain.application;

import java.util.Set;
import org.springframework.stereotype.Service;
import pl.pz.supplyblockchain.application.ports.RoleRegistry;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.Role;

@Service
public class RoleService {

    private final RoleRegistry roleRegistry;

    public RoleService(RoleRegistry roleRegistry) {
        this.roleRegistry = roleRegistry;
    }

    public Set<Role> rolesOf(String address) {
        return roleRegistry.rolesOf(address);
    }

    public void requireOperator(String address) {
        require(address, Role.OPERATOR_ROLE);
    }

    public void requireQaInspector(String address) {
        require(address, Role.QA_INSPECTOR_ROLE);
    }

    public boolean isAdmin(String address) {
        return roleRegistry.rolesOf(address).contains(Role.DEFAULT_ADMIN_ROLE);
    }

    private void require(String address, Role role) {
        if (!roleRegistry.rolesOf(address).contains(role)) {
            throw new DomainException(ErrorCode.ROLE_MISSING, role.name() + " is required");
        }
    }
}
