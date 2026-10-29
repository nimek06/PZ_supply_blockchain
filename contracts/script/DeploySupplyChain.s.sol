// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Script, console} from "forge-std/Script.sol";
import {SupplyChain} from "../src/SupplyChain.sol";

/// @notice Deploys SupplyChain and grants the initial roles.
/// Env:
///   DEPLOYER_PRIVATE_KEY  key that broadcasts (initial admin); never commit it
///   EXPECTED_CHAIN_ID     safety check, e.g. 31337 (Anvil) or 80002 (Amoy)
///   RELAYER_ADDRESS       optional, receives RELAYER_ROLE (default: deployer)
///   QA_INSPECTOR_ADDRESS  optional, receives QA_INSPECTOR_ROLE
///   ADMIN_ADDRESS         optional final admin; if different, the deployer renounces admin
///   MAX_DEADLINE_WINDOW   optional, seconds (default 3600, DEC-11)
/// Usage: forge script script/DeploySupplyChain.s.sol --rpc-url anvil --broadcast
contract DeploySupplyChain is Script {
    function run() external returns (SupplyChain sc) {
        uint256 deployerKey = vm.envUint("DEPLOYER_PRIVATE_KEY");
        address deployer = vm.addr(deployerKey);
        uint256 expectedChainId = vm.envUint("EXPECTED_CHAIN_ID");
        require(block.chainid == expectedChainId, "DeploySupplyChain: unexpected chainId");

        address relayer = vm.envOr("RELAYER_ADDRESS", deployer);
        address qaInspector = vm.envOr("QA_INSPECTOR_ADDRESS", address(0));
        address finalAdmin = vm.envOr("ADMIN_ADDRESS", deployer);
        uint256 window = vm.envOr("MAX_DEADLINE_WINDOW", uint256(3600));

        vm.startBroadcast(deployerKey);
        sc = new SupplyChain(deployer, window);
        sc.grantRole(sc.RELAYER_ROLE(), relayer);
        if (qaInspector != address(0)) sc.grantRole(sc.QA_INSPECTOR_ROLE(), qaInspector);
        if (finalAdmin != deployer) {
            sc.grantRole(sc.DEFAULT_ADMIN_ROLE(), finalAdmin);
            sc.renounceRole(sc.DEFAULT_ADMIN_ROLE(), deployer);
        }
        vm.stopBroadcast();

        require(sc.hasRole(sc.DEFAULT_ADMIN_ROLE(), finalAdmin), "admin role missing");
        require(sc.hasRole(sc.RELAYER_ROLE(), relayer), "relayer role missing");

        console.log("SupplyChain deployed at", address(sc));
        console.log("chainId", block.chainid);
        console.log("admin", finalAdmin);
        console.log("relayer", relayer);
    }
}
