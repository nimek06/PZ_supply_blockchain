package pl.pz.supplyblockchain.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import pl.pz.supplyblockchain.application.ports.BatchIdempotency;
import pl.pz.supplyblockchain.application.ports.CommandRepository;
import pl.pz.supplyblockchain.application.ports.DuplicateCommandException;
import pl.pz.supplyblockchain.application.ports.NewCreateBatchCommand;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.BatchNumber;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.StageCommand;

@Service
public class CreateBatchService {

    /** The request body is empty by contract, so the hash is stable across replays. */
    private static final String EMPTY_REQUEST_HASH = Hash.sha3String("");

    private final CommandRepository commandRepository;
    private final BatchNumberGenerator batchNumberGenerator;
    private final RoleService roleService;
    private final ChainProperties chainProperties;
    private final Clock clock;

    public CreateBatchService(
            CommandRepository commandRepository,
            BatchNumberGenerator batchNumberGenerator,
            RoleService roleService,
            ChainProperties chainProperties,
            Clock clock) {
        this.commandRepository = commandRepository;
        this.batchNumberGenerator = batchNumberGenerator;
        this.roleService = roleService;
        this.chainProperties = chainProperties;
        this.clock = clock;
    }

    public StageCommand create(String ownerAddress, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        roleService.requireOperator(ownerAddress);
        String owner = ownerAddress.toLowerCase(java.util.Locale.ROOT);
        DeploymentId deployment = chainProperties.deploymentId();

        Optional<BatchIdempotency> existing =
                commandRepository.findCreateBatchByIdempotency(deployment, owner, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get());
        }

        BatchNumber batchNumber = batchNumberGenerator.next(deployment);
        String batchId = Hash.sha3String(batchNumber.value());
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        StageCommand command = new StageCommand(
                UUID.randomUUID(),
                CommandType.CREATE_BATCH,
                owner,
                batchId,
                batchNumber.value(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CommandStatus.ACCEPTED,
                null,
                null,
                null,
                null,
                null,
                now,
                now);

        try {
            return commandRepository.saveNewCreateBatch(
                    new NewCreateBatchCommand(deployment, command, idempotencyKey, EMPTY_REQUEST_HASH));
        } catch (DuplicateCommandException conflict) {
            return resolveConcurrentConflict(deployment, owner, idempotencyKey);
        }
    }

    private StageCommand resolveConcurrentConflict(
            DeploymentId deployment, String owner, String idempotencyKey) {
        Optional<BatchIdempotency> existing =
                commandRepository.findCreateBatchByIdempotency(deployment, owner, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        throw new DomainException(
                ErrorCode.IDEMPOTENCY_CONFLICT,
                "a competing batch creation was accepted; retry the request");
    }

    private static StageCommand replay(BatchIdempotency existing) {
        if (!EMPTY_REQUEST_HASH.equalsIgnoreCase(existing.requestHash())) {
            throw new DomainException(
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used with a different request");
        }
        return existing.command();
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "Idempotency-Key header is required");
        }
        if (idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw new DomainException(
                    ErrorCode.INVALID_REQUEST, "Idempotency-Key must be between 8 and 128 characters");
        }
    }
}
