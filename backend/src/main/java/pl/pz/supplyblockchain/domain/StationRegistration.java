package pl.pz.supplyblockchain.domain;

/**
 * Result of {@code SupplyChain.getStation(address)}. An address that was never registered returns
 * all-zero values; {@code revokeStation} only clears {@code active} and keeps the other fields.
 * {@code expiresAt} values above {@link Long#MAX_VALUE} are saturated by the adapter.
 */
public record StationRegistration(boolean active, String stationId, long expiresAt, long stageMask) {

    public static final String ZERO_STATION_ID = "0x" + "0".repeat(64);

    public boolean everRegistered() {
        return !ZERO_STATION_ID.equals(stationId);
    }
}
