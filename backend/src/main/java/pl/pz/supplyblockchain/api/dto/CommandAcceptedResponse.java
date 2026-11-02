package pl.pz.supplyblockchain.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandAcceptedResponse(
        UUID commandId,
        String type,
        String status,
        String statusUrl) {
}
