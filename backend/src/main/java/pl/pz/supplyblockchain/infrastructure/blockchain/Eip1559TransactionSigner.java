package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.util.Collections;
import java.util.List;
import org.web3j.crypto.AccessListObject;
import org.web3j.crypto.AuthorizationTuple;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.Eip1559Transaction;
import pl.pz.supplyblockchain.application.ports.SignedTransaction;

@Component
public class Eip1559TransactionSigner {

    public SignedTransaction sign(Eip1559Transaction transaction, Credentials credentials) {
        List<AccessListObject> accessList = Collections.emptyList();
        List<AuthorizationTuple> authorizationList = Collections.emptyList();
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                transaction.chainId(),
                transaction.nonce(),
                transaction.gasLimit(),
                transaction.maxPriorityFeePerGas(),
                transaction.maxFeePerGas(),
                transaction.to(),
                transaction.value(),
                transaction.data(),
                accessList,
                authorizationList);
        byte[] signed = TransactionEncoder.signMessage(rawTransaction, credentials);
        return new SignedTransaction(
                Numeric.toHexString(signed),
                Numeric.toHexString(Hash.sha3(signed)));
    }
}
