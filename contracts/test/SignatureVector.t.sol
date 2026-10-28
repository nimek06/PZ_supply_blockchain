// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {SupplyChain} from "../src/SupplyChain.sol";
import {SupplyChainBase} from "./SupplyChain.t.sol";

/// @notice Cross-implementation EIP-712 checks for StageCompletion and StageApproval.
/// Domain: name "SupplyChain", version "1", chainId 31337, verifyingContract VECTOR_CONTRACT.
contract SignatureVectorTest is SupplyChainBase {
    string internal constant VECTOR_PATH = "../shared/fixtures/signature-vector.json";

    // Public, well-known Anvil dev keys (accounts #1 and #2). Test-only, never fund them on Amoy.
    uint256 internal constant ANVIL_KEY_1 =
        0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d;
    uint256 internal constant ANVIL_KEY_2 =
        0x5de4111afa1a4b94908f83103eb1f1706367c2e68ca870fc3fb9a804cdab365a;
    address internal constant ANVIL_ADDR_1 = 0x70997970C51812dc3A010C7d01b50e0d17dc79C8;
    address internal constant ANVIL_ADDR_2 = 0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC;

    // Provisional vector produced independently with `cast wallet sign --data` (Foundry 1.8.6,
    // alloy EIP-712 encoder). Replace by the shared fixture once it is published.
    bytes32 internal constant P_REPORT_HASH = keccak256("provisional-report");
    uint32 internal constant P_STAGE = 2;
    uint256 internal constant P_DEADLINE = 1_800_000_600;
    bytes internal constant P_STATION_SIG =
        hex"851302742ffcd6b79c30c3926ba8992f8287443c7cc6cc1d32bcb49d3f1b50364e349523372ef01e823edc8b2b8e636c9daf1eab41eef30f4d80de7f4bf73e6c1b";
    bytes internal constant P_OPERATOR_SIG =
        hex"8b46aba00f75c04a937337d71e197fb366c379d93a4534678aef167772adc6452f29fc658b535da13794f290a028b50da691b97d45ddfee3a91641d71dfcb2471b";

    function test_ProvisionalVector_RecoversStationAndOperator() public view {
        assertEq(vm.addr(ANVIL_KEY_1), ANVIL_ADDR_1);
        assertEq(vm.addr(ANVIL_KEY_2), ANVIL_ADDR_2);
        assertEq(batchId, 0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1);

        bytes32 completion =
            sc.hashStageCompletion(batchId, P_STAGE, P_REPORT_HASH, true, P_DEADLINE);
        bytes32 approval = sc.hashStageApproval(batchId, P_STAGE, P_REPORT_HASH, P_DEADLINE);
        // Digests computed with `cast abi-encode` + `cast keccak` (manual EIP-712 encoding).
        assertEq(completion, 0xf66529bc5ef30dc02cab89a91a2f77a091b1ef372f72cc42975d81c429462b24);
        assertEq(approval, 0xc32c5fb3c18131172585905622d37abd7784569fd01e450b32d9bd34676184e0);
        assertEq(ECDSA.recover(completion, P_STATION_SIG), ANVIL_ADDR_1);
        assertEq(ECDSA.recover(approval, P_OPERATOR_SIG), ANVIL_ADDR_2);

        // vm.sign is deterministic (RFC 6979), so it must reproduce the cast signatures byte-for-byte.
        assertEq(_sign(ANVIL_KEY_1, completion), P_STATION_SIG);
        assertEq(_sign(ANVIL_KEY_2, approval), P_OPERATOR_SIG);
    }

    function test_ProvisionalVector_ClosesControlStage() public {
        vm.startPrank(admin);
        sc.registerStation(ANVIL_ADDR_1, keccak256("ST-VECTOR"), uint64(P_DEADLINE + 1 days), 0x0F);
        sc.grantRole(sc.QA_INSPECTOR_ROLE(), ANVIL_ADDR_2);
        vm.stopPrank();
        _completeValid(0);
        _completeValid(1);

        vm.warp(P_DEADLINE - 60);
        vm.expectEmit(true, true, false, true, address(sc));
        emit SupplyChain.StageCompleted(
            batchId, P_STAGE, ANVIL_ADDR_1, ANVIL_ADDR_2, P_REPORT_HASH, CID
        );
        _submit(P_STAGE, P_REPORT_HASH, true, P_DEADLINE, P_STATION_SIG, P_DEADLINE, P_OPERATOR_SIG);
        _assertBatch(3, false);
    }

    /// @dev Expected JSON paths (agreed with the Python vector author, see test/README.md):
    /// .domain.{chainId,verifyingContract}
    /// .stageCompletion.message.{batchId,stageId,reportHash,qaPassed,deadline}
    /// .stageCompletion.{digest,signature,signer}
    /// .stageApproval.message.{batchId,stageId,reportHash,deadline}
    /// .stageApproval.{digest,signature,signer}
    function test_SharedVector() public {
        if (!vm.exists(VECTOR_PATH)) {
            vm.skip(true, "shared/fixtures/signature-vector.json not published yet");
        }
        string memory json = vm.readFile(VECTOR_PATH);

        assertEq(vm.parseJsonUint(json, ".domain.chainId"), block.chainid, "chainId");
        assertEq(
            vm.parseJsonAddress(json, ".domain.verifyingContract"), address(sc), "verifyingContract"
        );

        bytes32 b = vm.parseJsonBytes32(json, ".stageCompletion.message.batchId");
        uint32 s = uint32(vm.parseJsonUint(json, ".stageCompletion.message.stageId"));
        bytes32 h = vm.parseJsonBytes32(json, ".stageCompletion.message.reportHash");
        bool passed = vm.parseJsonBool(json, ".stageCompletion.message.qaPassed");
        uint256 d = vm.parseJsonUint(json, ".stageCompletion.message.deadline");
        bytes32 digest = sc.hashStageCompletion(b, s, h, passed, d);
        assertEq(digest, vm.parseJsonBytes32(json, ".stageCompletion.digest"), "completion digest");
        assertEq(
            ECDSA.recover(digest, vm.parseJsonBytes(json, ".stageCompletion.signature")),
            vm.parseJsonAddress(json, ".stageCompletion.signer"),
            "station signer"
        );

        b = vm.parseJsonBytes32(json, ".stageApproval.message.batchId");
        s = uint32(vm.parseJsonUint(json, ".stageApproval.message.stageId"));
        h = vm.parseJsonBytes32(json, ".stageApproval.message.reportHash");
        d = vm.parseJsonUint(json, ".stageApproval.message.deadline");
        digest = sc.hashStageApproval(b, s, h, d);
        assertEq(digest, vm.parseJsonBytes32(json, ".stageApproval.digest"), "approval digest");
        assertEq(
            ECDSA.recover(digest, vm.parseJsonBytes(json, ".stageApproval.signature")),
            vm.parseJsonAddress(json, ".stageApproval.signer"),
            "operator signer"
        );
    }
}
