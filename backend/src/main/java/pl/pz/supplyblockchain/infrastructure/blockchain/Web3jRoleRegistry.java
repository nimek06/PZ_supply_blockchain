package pl.pz.supplyblockchain.infrastructure.blockchain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Numeric;
import pl.pz.supplyblockchain.application.ports.RoleRegistry;
import pl.pz.supplyblockchain.config.ChainProperties;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;
import pl.pz.supplyblockchain.domain.Role;

@Component
public class Web3jRoleRegistry implements RoleRegistry {

    private static final Map<Role, byte[]> ROLE_HASHES = roleHashes();

    private final Web3j web3j;
    private final String contractAddress;

    public Web3jRoleRegistry(Web3j web3j, ChainProperties chainProperties) {
        this.web3j = web3j;
        this.contractAddress = chainProperties.contractAddress();
    }

    @Override
    public Set<Role> rolesOf(String address) {
        if (address == null || address.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_REQUEST, "address is required");
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Role role : Role.values()) {
            if (hasRole(role, address)) {
                roles.add(role);
            }
        }
        return Collections.unmodifiableSet(roles);
    }

    private boolean hasRole(Role role, String address) {
        Function function = new Function(
                "hasRole",
                List.of(new Bytes32(ROLE_HASHES.get(role)), new Address(address.toLowerCase(Locale.ROOT))),
                List.of());
        Transaction call = Transaction.createEthCallTransaction(
                null, contractAddress, FunctionEncoder.encode(function));
        try {
            EthCall response = web3j.ethCall(call, DefaultBlockParameterName.LATEST).send();
            if (response.hasError() || response.getValue() == null || response.getValue().isBlank()) {
                throw dependencyUnavailable();
            }
            return Numeric.toBigInt(response.getValue()).signum() != 0;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof DomainException domainException) {
                throw domainException;
            }
            throw dependencyUnavailable();
        }
    }

    private static DomainException dependencyUnavailable() {
        return new DomainException(
                ErrorCode.DEPENDENCY_UNAVAILABLE, "on-chain role registry is unavailable");
    }

    private static Map<Role, byte[]> roleHashes() {
        Map<Role, byte[]> hashes = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            hashes.put(role, Hash.sha3(role.name().getBytes(StandardCharsets.UTF_8)));
        }
        return Collections.unmodifiableMap(hashes);
    }
}
