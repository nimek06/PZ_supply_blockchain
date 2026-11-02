package pl.pz.supplyblockchain.application.auth;

import pl.pz.supplyblockchain.domain.AuthSession;

public record NewAuthSession(AuthSession session, String token) {
}
