// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Script, console} from "forge-std/Script.sol";
import {SupplyChain} from "../src/SupplyChain.sol";

/// @notice Registers (or updates) a station in the on-chain registry. Idempotent: skips the
/// transaction when the registry already holds exactly the requested values.
/// Env:
///   ADMIN_PRIVATE_KEY      key holding DEFAULT_ADMIN_ROLE
///   SUPPLY_CHAIN_ADDRESS   deployed contract
///   STATION_ADDRESS        station public address (its key stays on the station)
///   STATION_ID             bytes32 technical id (see docs/station-identity.md)
///   STATION_EXPIRES_AT     optional Unix seconds (default: now + 365 days)
///   STATION_STAGE_MASK     optional bit mask, bit i = stage i (default 0x0F, all stages)
/// Usage: forge script script/RegisterStation.s.sol --rpc-url anvil --broadcast
contract RegisterStation is Script {
    function run() external {
        uint256 adminKey = vm.envUint("ADMIN_PRIVATE_KEY");
        SupplyChain sc = SupplyChain(vm.envAddress("SUPPLY_CHAIN_ADDRESS"));
        address station = vm.envAddress("STATION_ADDRESS");
        bytes32 stationId = vm.envBytes32("STATION_ID");
        uint64 expiresAt = uint64(vm.envOr("STATION_EXPIRES_AT", block.timestamp + 365 days));
        uint32 stageMask = uint32(vm.envOr("STATION_STAGE_MASK", uint256(0x0F)));

        (bool active, bytes32 currentId, uint64 currentExp, uint32 currentMask) =
            sc.getStation(station);
        if (active && currentId == stationId && currentExp == expiresAt && currentMask == stageMask)
        {
            console.log("Station already registered with the same values", station);
            return;
        }

        vm.startBroadcast(adminKey);
        sc.registerStation(station, stationId, expiresAt, stageMask);
        vm.stopBroadcast();

        console.log("Station registered", station);
        console.log("expiresAt", expiresAt);
        console.log("stageMask", stageMask);
    }
}
