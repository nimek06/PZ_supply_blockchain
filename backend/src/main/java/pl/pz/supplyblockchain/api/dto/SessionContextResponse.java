package pl.pz.supplyblockchain.api.dto;

import java.util.List;

public record SessionContextResponse(
        String address,
        List<String> roles,
        long expiresAt,
        long chainId,
        String verifyingContract) {
}
