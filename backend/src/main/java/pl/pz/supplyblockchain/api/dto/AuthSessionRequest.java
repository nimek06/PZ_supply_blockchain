package pl.pz.supplyblockchain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AuthSessionRequest(UUID challengeId, String message, String signature) {
}
