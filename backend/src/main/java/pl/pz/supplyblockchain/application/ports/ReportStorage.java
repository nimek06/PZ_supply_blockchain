package pl.pz.supplyblockchain.application.ports;

/**
 * Report storage port (DEC-14). SKETCH for week 1: no adapter yet; Kubo (local, CI) and Pinata (demo)
 * adapters arrive in week 4.
 *
 * <p>Contract:
 * <ul>
 *   <li>Input is exactly the JCS bytes of report v1 ({@link CanonicalReport#jcsBytes()}); adapters upload them
 *       as a file and must never re-serialize them as a JSON object.</li>
 *   <li>Output is a validated CID (CIDv0 or CIDv1), never a gateway URL; an empty or malformed CID is an error.</li>
 *   <li>Idempotent by content: storing the same bytes again returns the same content, so a controlled retry is
 *       safe, but the caller must persist the CID before ordering the blockchain transaction and must not create
 *       another stage command on retry.</li>
 *   <li>The CID is not the report hash; never compare them as text (docs/protocol.md).</li>
 * </ul>
 */
public interface ReportStorage {

    /**
     * Pins the canonical report bytes.
     *
     * @throws ReportStorageException with {@link ReportStorageException.Reason} describing whether a retry is allowed
     */
    StoredReport store(byte[] canonicalReportJcs);
}
