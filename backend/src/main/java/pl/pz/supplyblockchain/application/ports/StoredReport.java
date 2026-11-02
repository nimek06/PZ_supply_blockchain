package pl.pz.supplyblockchain.application.ports;

/**
 * Result of {@link ReportStorage#store(byte[])}.
 *
 * @param cid validated CIDv0 ({@code Qm...}) or CIDv1 (base32 {@code b...}) string
 * @param provider storage that pinned the bytes, e.g. {@code kubo} or {@code pinata}, kept for diagnostics
 */
public record StoredReport(String cid, String provider) {
}
