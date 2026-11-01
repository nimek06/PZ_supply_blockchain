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

    public CommandQueryService(CommandRepository commandRepository) {
        this.commandRepository = commandRepository;
    }

    public StageCommand getOwned(UUID id, String ownerAddress) {
        StageCommand command = commandRepository.findById(id)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "command not found"));
        if (!command.ownerAddress().equalsIgnoreCase(ownerAddress)) {
            // Do not reveal that the command exists.
            throw new DomainException(ErrorCode.NOT_FOUND, "command not found");
        }
        return command;
    }

    public String normalizeAddress(String address) {
        return address.toLowerCase(Locale.ROOT);
    }
}
