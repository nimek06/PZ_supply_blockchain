package pl.pz.supplyblockchain.application.session;

import java.util.List;
import pl.pz.supplyblockchain.domain.Role;

public record SessionContext(
        String address,
        List<Role> roles,
        long expiresAt,
        long chainId,
        String verifyingContract) {
}
