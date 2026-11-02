package pl.pz.supplyblockchain.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AuthChallengeRequest(String address) {
}
