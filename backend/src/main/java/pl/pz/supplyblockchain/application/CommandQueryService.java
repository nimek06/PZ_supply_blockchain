package pl.pz.supplyblockchain.application;

import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

@Service
public class CommandQueryService {

    private final CommandRepository commandRepository;
    private final RoleService roleService;

    public CommandQueryService(CommandRepository commandRepository, RoleService roleService) {
        this.commandRepository = commandRepository;
        this.roleService = roleService;
    }

    /**
     * Owner sees the command. A current administrator also sees it. Everybody else gets 404 so
     * that the existence of another user's command is not revealed.
     */
    public StageCommand getVisible(UUID id, String requesterAddress) {
        StageCommand command = commandRepository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "command not found"));
        if (command.ownerAddress().equalsIgnoreCase(requesterAddress)) {
            return command;
        }
        if (roleService.isAdmin(requesterAddress)) {
            return command;
        }
        throw new DomainException(ErrorCode.NOT_FOUND, "command not found");
    }

    public String normalizeAddress(String address) {
        return address.toLowerCase(Locale.ROOT);
    }
}
