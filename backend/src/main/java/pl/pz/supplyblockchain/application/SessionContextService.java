package pl.pz.supplyblockchain.application;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.pz.supplyblockchain.application.session.SessionContext;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.AuthSession;

@Service
public class SessionContextService {

    private final AuthService authService;
    private final RoleService roleService;
    private final ChainProperties chainProperties;

    public SessionContextService(
            AuthService authService, RoleService roleService, ChainProperties chainProperties) {
        this.authService = authService;
        this.roleService = roleService;
        this.chainProperties = chainProperties;
    }

    public SessionContext current(String authorizationHeader) {
        AuthSession session = authService.authenticateSession(authorizationHeader);
        List<pl.pz.supplyblockchain.domain.Role> roles =
                List.copyOf(roleService.rolesOf(session.address()));
        return new SessionContext(
                session.address(),
                roles,
                session.expiresAt().getEpochSecond(),
                session.chainId(),
                chainProperties.contractAddress());
    }
}
