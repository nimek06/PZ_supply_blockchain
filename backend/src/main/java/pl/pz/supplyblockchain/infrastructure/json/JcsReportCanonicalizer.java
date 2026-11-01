package pl.pz.supplyblockchain.infrastructure.json;

import org.erdtman.jcs.JsonCanonicalizer;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.CanonicalReport;
import pl.pz.supplyblockchain.application.ports.ReportCanonicalizer;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.TelemetryReport;
import tools.jackson.databind.ObjectMapper;

@Component
public class JcsReportCanonicalizer implements ReportCanonicalizer {

    private final ObjectMapper objectMapper;

    public JcsReportCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CanonicalReport canonicalize(TelemetryReport report) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(report);
            byte[] canonical = new JsonCanonicalizer(json).getEncodedUTF8();
            String hash = Numeric.toHexString(Hash.sha3(canonical));
            return new CanonicalReport(canonical, hash);
        } catch (IOException | RuntimeException exception) {
            throw new DomainException(ErrorCode.SCHEMA_VIOLATION,
                    "report could not be canonicalized: " + exception.getMessage());
        }
    }
}
