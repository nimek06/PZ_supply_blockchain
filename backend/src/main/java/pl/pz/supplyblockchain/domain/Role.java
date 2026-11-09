package pl.pz.supplyblockchain.domain;

/**
 * Roles read from the on-chain AccessControl registry. They are never cached and never stored as
 * a durable permission in the backend.
 */
public enum Role {
    OPERATOR_ROLE,
    QA_INSPECTOR_ROLE,
    RELAYER_ROLE,
    DEFAULT_ADMIN_ROLE
}
