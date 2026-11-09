package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import pl.pz.supplyblockchain.application.ports.NewCreateBatchCommand;
import pl.pz.supplyblockchain.application.ports.NewStageCommand;
import pl.pz.supplyblockchain.domain.CommandOutcome;
import pl.pz.supplyblockchain.domain.CommandStatus;
import pl.pz.supplyblockchain.domain.CommandType;
import pl.pz.supplyblockchain.domain.DeploymentId;
import pl.pz.supplyblockchain.domain.StageCommand;

@Entity
@Table(name = "commands")
class StageCommandEntity {

    @Id
    private UUID id;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "contract_address", nullable = false, length = 42)
    private String contractAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private CommandType type;

    @Column(name = "owner_address", nullable = false, length = 42)
    private String ownerAddress;

    @Column(name = "batch_id", length = 66)
    private String batchId;

    @Column(name = "batch_number", length = 32)
    private String batchNumber;

    @Column(name = "stage_id")
    private Integer stageId;

    @Column(name = "station_address", length = 42)
    private String stationAddress;

    @Column(name = "station_certificate_id", length = 128)
    private String stationCertificateId;

    @Column(name = "report_hash", length = 66)
    private String reportHash;

    @Column(name = "report_json")
    private String reportJson;

    @Column(name = "report_jcs")
    private byte[] reportJcs;

    @Column(name = "station_signature")
    private String stationSignature;

    @Column(name = "signed_payload_digest", length = 66)
    private String signedPayloadDigest;

    @Column(name = "deadline")
    private Long deadline;

    @Column(name = "qa_passed", nullable = false)
    private Boolean qaPassed;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 66)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CommandStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 16)
    private CommandOutcome outcome;

    @Column(name = "cid")
    private String cid;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "retry_allowed_after")
    private Long retryAllowedAfter;

    @Column(name = "retry_at")
    private Instant retryAt;

    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StageCommandEntity() {
    }

    static StageCommandEntity from(NewStageCommand newCommand) {
        StageCommandEntity entity = common(
                newCommand.command(), newCommand.deploymentId());
        entity.reportJson = newCommand.reportJson();
        entity.reportJcs = newCommand.canonicalReportJcs();
        entity.stationSignature = newCommand.stationSignature();
        return entity;
    }

    static StageCommandEntity from(NewCreateBatchCommand newCommand) {
        StageCommandEntity entity = common(
                newCommand.command(), newCommand.deploymentId());
        entity.idempotencyKey = newCommand.idempotencyKey();
        entity.requestHash = newCommand.requestHash();
        return entity;
    }

    private static StageCommandEntity common(StageCommand command, DeploymentId deploymentId) {
        StageCommandEntity entity = new StageCommandEntity();
        entity.id = command.id();
        entity.chainId = deploymentId.chainId();
        entity.contractAddress = deploymentId.contractAddress();
        entity.type = command.type();
        entity.ownerAddress = command.ownerAddress();
        entity.batchId = command.batchId();
        entity.batchNumber = command.batchNumber();
        entity.stageId = command.stageId();
        entity.stationAddress = command.stationAddress();
        entity.stationCertificateId = command.stationCertificateId();
        entity.reportHash = command.reportHash();
        entity.signedPayloadDigest = command.signedPayloadDigest();
        entity.deadline = command.deadline();
        entity.qaPassed = command.qaPassed() == null ? Boolean.FALSE : command.qaPassed();
        entity.status = command.status();
        entity.outcome = command.outcome();
        entity.cid = command.cid();
        entity.failureCode = command.failureCode();
        entity.retryAt = command.retryAt();
        entity.retryAllowedAfter = command.retryAllowedAfter();
        entity.createdAt = command.createdAt();
        entity.updatedAt = command.updatedAt();
        return entity;
    }

    StageCommand toDomain() {
        return new StageCommand(
                id,
                type,
                ownerAddress,
                batchId,
                batchNumber,
                stageId,
                stationAddress,
                stationCertificateId,
                reportHash,
                signedPayloadDigest,
                deadline,
                qaPassed,
                status,
                outcome,
                cid,
                failureCode,
                retryAt,
                retryAllowedAfter,
                createdAt,
                updatedAt);
    }

    String requestHash() {
        return requestHash;
    }
}
