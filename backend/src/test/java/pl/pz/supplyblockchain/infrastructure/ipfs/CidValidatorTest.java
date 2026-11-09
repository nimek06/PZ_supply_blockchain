package pl.pz.supplyblockchain.infrastructure.ipfs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CidValidatorTest {

    // CIDs returned by Kubo v0.32.1 for the same report bytes (docs/weekly_plans/week1/kuba-ipfs-notes.md)
    private static final String V0 = "QmYc61wGUCUtLBJYN97wBHsAFZN1bkri1DsPnDeymHuDM9";
    private static final String V1_RAW = "bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngy";
    // the V0 multihash re-encoded as CIDv1 dag-pb
    private static final String V1_DAG_PB = "bafybeieyrezfgd7hwl35apiteij5sxpw26kek5k3ptguk4z5a5xrpcpdma";

    @ParameterizedTest
    @ValueSource(strings = {V0, V1_RAW, V1_DAG_PB})
    void acceptsCidsReturnedByKubo(String cid) {
        assertThat(CidValidator.isValid(cid)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Qm",
            "QmYc61wGUCUtLBJYN97wBHsAFZN1bkri1DsPnDeymHuDM",     // one character short
            "QmYc61wGUCUtLBJYN97wBHsAFZN1bkri1DsPnDeymHuDM0",    // '0' is not base58
            "bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ng",   // truncated
            "bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngyaa", // extra bytes
            "BAFKREIC6HFD5NLFCH6SJUTXN63TBJPTJ4X4PXO5EEOCYLMHB5GKO2Q4NGY",  // base32upper is not used by us
            "bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ng1",  // '1' is not base32
            "zdj7WhuEjrB52m1BisYCtmjH1hSKa7yZ3jEZ9JcXaFRD51wVz",           // base58 CIDv1
            "ipfs://bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngy",
            "https://ipfs.io/ipfs/bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngy",
            "0x4707421ac9d17015745b8d29d2684f271eb6d37cc2a22f7f01456b88a894e97d" // a report hash, not a CID
    })
    void rejectsAnythingElse(String cid) {
        assertThat(CidValidator.isValid(cid)).isFalse();
    }
}
