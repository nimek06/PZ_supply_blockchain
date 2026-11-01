package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.StageCompletionVerifier;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.SignatureVerification;
import pl.pz.supplyblockchain.domain.StageApprovalMessage;
import pl.pz.supplyblockchain.domain.StageCompletionMessage;

@Component
public class Web3jEip712Verifier implements StageCompletionVerifier {

    private static final String DOMAIN_TYPE =
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)";
    private static final String STAGE_COMPLETION_TYPE =
            "StageCompletion(bytes32 batchId,uint32 stageId,bytes32 reportHash,bool qaPassed,uint256 deadline)";
    private static final String STAGE_APPROVAL_TYPE =
            "StageApproval(bytes32 batchId,uint32 stageId,bytes32 reportHash,uint256 deadline)";

    private final byte[] domainSeparator;
    private final byte[] completionTypeHash;
    private final byte[] approvalTypeHash;

    public Web3jEip712Verifier(ChainProperties chainProperties) {
        this.domainSeparator = keccak(concat(
                keccak(utf8(DOMAIN_TYPE)),
                keccak(utf8(chainProperties.domainName())),
                keccak(utf8(chainProperties.domainVersion())),
                word(BigInteger.valueOf(chainProperties.chainId())),
                addressWord(chainProperties.contractAddress())));
        this.completionTypeHash = keccak(utf8(STAGE_COMPLETION_TYPE));
        this.approvalTypeHash = keccak(utf8(STAGE_APPROVAL_TYPE));
    }

    @Override
    public SignatureVerification verifyCompletion(StageCompletionMessage message, String signature) {
        byte[] structuredData = concat(
                completionTypeHash,
                bytes32(message.batchId(), "batchId"),
                word(BigInteger.valueOf(message.stageId())),
                bytes32(message.reportHash(), "reportHash"),
                word(message.qaPassed() ? BigInteger.ONE : BigInteger.ZERO),
                word(BigInteger.valueOf(message.deadline())));
        return verify(structuredData, signature);
    }

    @Override
    public SignatureVerification verifyApproval(StageApprovalMessage message, String signature) {
        byte[] structuredData = concat(
                approvalTypeHash,
                bytes32(message.batchId(), "batchId"),
                word(BigInteger.valueOf(message.stageId())),
                bytes32(message.reportHash(), "reportHash"),
                word(BigInteger.valueOf(message.deadline())));
        return verify(structuredData, signature);
    }

    private SignatureVerification verify(byte[] structuredData, String signature) {
        byte[] structHash = keccak(structuredData);
        byte[] digest = keccak(concat(new byte[]{0x19, 0x01}, domainSeparator, structHash));
        String signer = EcdsaRecovery.recoverAddress(digest, signature);
        return new SignatureVerification(signer, Numeric.toHexString(digest));
    }

    private static byte[] bytes32(String value, String field) {
        if (value == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " is required");
        }
        byte[] bytes;
        try {
            bytes = Numeric.hexStringToByteArray(value);
        } catch (RuntimeException exception) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " must be hex");
        }
        if (bytes.length != 32) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " must be bytes32");
        }
        return bytes;
    }

    private static byte[] word(BigInteger value) {
        if (value.signum() < 0) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "uint256 fields must not be negative");
        }
        return Numeric.toBytesPadded(value, 32);
    }

    private static byte[] addressWord(String address) {
        if (address == null) {
            throw new IllegalArgumentException("verifying contract address is required");
        }
        return Numeric.toBytesPadded(Numeric.toBigInt(address), 32);
    }

    private static byte[] keccak(byte[] value) {
        return Hash.sha3(value);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Arrays.stream(values).mapToInt(value -> value.length).sum());
        for (byte[] value : values) {
            output.writeBytes(value);
        }
        return output.toByteArray();
    }
}
