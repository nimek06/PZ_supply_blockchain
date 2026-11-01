package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.StageApprovalMessage;
import pl.pz.supplyblockchain.domain.StageCompletionMessage;
import pl.pz.supplyblockchain.domain.SignatureVerification;

public interface StageCompletionVerifier {

    SignatureVerification verifyCompletion(StageCompletionMessage message, String signature);

    SignatureVerification verifyApproval(StageApprovalMessage message, String signature);
}
