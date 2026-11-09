package pl.pz.supplyblockchain.application.ports;

import java.util.Set;
import pl.pz.supplyblockchain.domain.Role;

public interface RoleRegistry {

    /**
     * Reads the current roles of an address from the chain. Implementations must not cache the
     * result: a revoked role has to be visible on the next request.
     *
     * @throws pl.pz.supplyblockchain.domain.DomainException with
     *     {@code DEPENDENCY_UNAVAILABLE} when the RPC endpoint cannot answer
     */
    Set<Role> rolesOf(String address);
}
