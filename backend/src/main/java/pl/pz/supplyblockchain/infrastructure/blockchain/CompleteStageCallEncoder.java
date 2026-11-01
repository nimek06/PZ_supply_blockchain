package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.CompleteStageCall;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@Component
public class CompleteStageCallEncoder {

    public String encode(CompleteStageCall call) {
        List<Type> inputs = List.of(
                bytes32(call.batchId(), "batchId"),
                new Uint32(BigInteger.valueOf(call.stageId())),
                bytes32(call.reportHash(), "reportHash"),
                new Bool(call.qaPassed()),
                new Uint256(BigInteger.valueOf(call.deadline())),
                new Utf8String(call.ipfsCid()),
                new DynamicBytes(signature(call.stationSignature(), "stationSignature")),
                new Uint256(BigInteger.valueOf(call.approvalDeadline())),
                new DynamicBytes(signature(call.approvalSignature(), "approvalSignature")));
        Function function = new Function("completeStage", inputs, Collections.emptyList());
        return FunctionEncoder.encode(function);
    }

    private static Bytes32 bytes32(String value, String field) {
        if (value == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " is required");
        }
        byte[] bytes = Numeric.hexStringToByteArray(value);
        if (bytes.length != 32) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " must be bytes32");
        }
        return new Bytes32(bytes);
    }

    private static byte[] signature(String value, String field) {
        if (value == null) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " is required");
        }
        byte[] bytes = Numeric.hexStringToByteArray(value);
        if (bytes.length != 65) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, field + " must be 65 bytes");
        }
        return bytes;
    }
}
