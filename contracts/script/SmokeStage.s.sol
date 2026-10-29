// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Script, console} from "forge-std/Script.sol";
import {SupplyChain} from "../src/SupplyChain.sol";

/// @notice Smoke test: creates a batch and completes stage 0 with a station-signed report.
/// The station key only signs EIP-712 off-chain (no gas); the relayer key broadcasts.
/// Env:
///   RELAYER_PRIVATE_KEY    key holding RELAYER_ROLE (pays gas)
///   STATION_PRIVATE_KEY    registered station key (TEST key only, never a production key)
///   SUPPLY_CHAIN_ADDRESS   deployed contract
///   BATCH_NUMBER           e.g. B-2026-0001 (must be new)
///   IPFS_CID               optional CID to record (default: a placeholder test CID)
///   DEADLINE_SECONDS       optional deadline offset (default 900, must fit maxDeadlineWindow)
/// Usage: forge script script/SmokeStage.s.sol --rpc-url amoy --broadcast --slow
contract SmokeStage is Script {
    function run() external {
        uint256 relayerKey = vm.envUint("RELAYER_PRIVATE_KEY");
        uint256 stationKey = vm.envUint("STATION_PRIVATE_KEY");
        SupplyChain sc = SupplyChain(vm.envAddress("SUPPLY_CHAIN_ADDRESS"));
        string memory batchNumber = vm.envString("BATCH_NUMBER");
        string memory cid = vm.envOr(
            "IPFS_CID", string("bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy")
        );
        uint256 deadline = block.timestamp + vm.envOr("DEADLINE_SECONDS", uint256(900));

        bytes32 batchId = keccak256(bytes(batchNumber));
        bytes32 reportHash = keccak256(abi.encodePacked("smoke-report:", batchNumber));
        bytes32 digest = sc.hashStageCompletion(batchId, 0, reportHash, true, deadline);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(stationKey, digest);

        vm.startBroadcast(relayerKey);
        sc.createBatch(batchNumber);
        sc.completeStage(
            batchId, 0, reportHash, true, deadline, cid, abi.encodePacked(r, s, v), 0, ""
        );
        vm.stopBroadcast();

        (bool exists, uint32 nextStage,) = sc.getBatch(batchId);
        require(exists && nextStage == 1, "SmokeStage: stage 0 not completed");
        console.log("Batch", batchNumber);
        console.logBytes32(batchId);
        console.log("station", vm.addr(stationKey));
        console.log("nextStage", nextStage);
    }
}
