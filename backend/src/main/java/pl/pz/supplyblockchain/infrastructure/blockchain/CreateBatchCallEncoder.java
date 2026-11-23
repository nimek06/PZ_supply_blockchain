package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@Component
public class CreateBatchCallEncoder {

    public String encode(String batchNumber) {
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION, "batchNumber is required");
        }
        Function function = new Function(
                "createBatch", List.of(new Utf8String(batchNumber)), Collections.emptyList());
        return FunctionEncoder.encode(function);
    }
}
