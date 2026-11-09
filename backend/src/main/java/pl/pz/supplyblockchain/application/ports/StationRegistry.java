package pl.pz.supplyblockchain.application.ports;

import pl.pz.supplyblockchain.domain.StationRegistration;

/**
 * Read-only view of the on-chain station registry ({@code SupplyChain.getStation}).
 * Implementations never return {@code null}: an unknown address is a registration with
 * {@code active = false} and a zero stationId. Calls are idempotent and may be retried.
 */
public interface StationRegistry {

    /**
     * @param stationAddress lowercase {@code 0x} address
     * @throws StationRegistryException when the registry cannot be read or returns an invalid response
     */
    StationRegistration getStation(String stationAddress);
}
