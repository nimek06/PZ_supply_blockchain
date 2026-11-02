package pl.pz.supplyblockchain.infrastructure.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import pl.pz.supplyblockchain.application.ports.Eip1559Transaction;
import pl.pz.supplyblockchain.application.ports.SignedTransaction;

class Eip1559TransactionSignerTest {

    private final Eip1559TransactionSigner signer = new Eip1559TransactionSigner();

    @Test
    void signsEip1559Transaction() {
        ECKeyPair keyPair = ECKeyPair.create(BigInteger.valueOf(1));
        Credentials credentials = Credentials.create(keyPair);

        Eip1559Transaction transaction = new Eip1559Transaction(
                31337,
                BigInteger.ZERO,
                BigInteger.valueOf(100_000),
                BigInteger.valueOf(1_000_000_000),
                BigInteger.valueOf(2_000_000_000),
                "0x5fbdb2315678afecb367f032d93f642f64180aa3",
                BigInteger.ZERO,
                "0x12345678");

        SignedTransaction signed = signer.sign(transaction, credentials);

        assertThat(signed.rawTransaction()).startsWith("0x");
        assertThat(signed.transactionHash()).startsWith("0x").hasSize(66);
    }
}
