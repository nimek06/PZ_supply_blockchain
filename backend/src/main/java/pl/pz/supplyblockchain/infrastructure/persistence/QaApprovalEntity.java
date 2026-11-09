package pl.pz.supplyblockchain.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pl.pz.supplyblockchain.domain.QaApproval;
import pl.pz.supplyblockchain.domain.QaApprovalStatus;

@Entity
@Table(name = "qa_approvals")
class QaApprovalEntity {

    @Id
    private UUID id;

    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(name = "operator_address", nullable = false, length = 42)
    private String operatorAddress;

    @Column(name = "approval_deadline", nullable = false)
    private long approvalDeadline;

    @Column(name = "signature", nullable = false)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private QaApprovalStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QaApprovalEntity() {
    }

    static QaApprovalEntity from(QaApproval approval) {
        QaApprovalEntity entity = new QaApprovalEntity();
        entity.id = approval.id();
        entity.commandId = approval.commandId();
        entity.operatorAddress = approval.operatorAddress();
        entity.approvalDeadline = approval.approvalDeadline();
        entity.signature = approval.signature();
        entity.status = approval.status();
        entity.createdAt = approval.createdAt();
        return entity;
    }

    QaApproval toDomain() {
        return new QaApproval(id, commandId, operatorAddress, approvalDeadline, signature, status, createdAt);
    }
}
