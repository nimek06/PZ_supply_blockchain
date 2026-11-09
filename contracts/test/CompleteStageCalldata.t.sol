// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {SupplyChain} from "../src/SupplyChain.sol";
import {SupplyChainBase} from "./SupplyChain.t.sol";

/// @notice ABI compatibility: `shared/fixtures/complete-stage-calldata.json` holds completeStage
/// inputs and calldata encoded independently by `cast calldata`. Solidity (here) and the Java
/// `CompleteStageCallEncoder` must produce identical bytes, and the raw bytes must execute.
contract CompleteStageCalldataTest is SupplyChainBase {
    string internal constant FIXTURE_PATH = "../shared/fixtures/complete-stage-calldata.json";
    string internal constant VECTOR_PATH = "../shared/fixtures/signature-vector.json";
    uint256 internal constant CASE_COUNT = 5;

    // Public Anvil dev accounts #1 (station) and #2 (QA operator) used by the shared vector.
    address internal constant ANVIL_ADDR_1 = 0x70997970C51812dc3A010C7d01b50e0d17dc79C8;
    address internal constant ANVIL_ADDR_2 = 0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC;

    struct CallInput {
        bytes32 batchId;
        uint32 stageId;
        bytes32 reportHash;
        bool qaPassed;
        uint256 deadline;
        string ipfsCid;
        bytes stationSignature;
        uint256 approvalDeadline;
        bytes approvalSignature;
    }

    string internal json;

    function setUp() public override {
        super.setUp();
        json = vm.readFile(FIXTURE_PATH);
    }

    function test_FixtureHeaderMatchesContract() public view {
        assertEq(
            vm.parseJsonString(json, ".function"),
            "completeStage(bytes32,uint32,bytes32,bool,uint256,string,bytes,uint256,bytes)"
        );
        assertEq(bytes4(vm.parseJsonBytes(json, ".selector")), SupplyChain.completeStage.selector);
        assertEq(vm.parseJsonUint(json, ".domain.chainId"), block.chainid, "chainId");
        assertEq(
            vm.parseJsonAddress(json, ".domain.verifyingContract"), address(sc), "verifyingContract"
        );
        assertEq(keccak256(bytes(vm.parseJsonString(json, ".batchNumber"))), batchId, "batchId");
        assertTrue(vm.keyExistsJson(json, _case(CASE_COUNT - 1, "name")), "too few cases");
        assertFalse(vm.keyExistsJson(json, _case(CASE_COUNT, "name")), "untested extra case");
    }

    function test_SolidityEncodingMatchesFixture() public view {
        for (uint256 i = 0; i < CASE_COUNT; i++) {
            CallInput memory c = _input(i);
            bytes memory encoded = abi.encodeCall(
                SupplyChain.completeStage,
                (
                    c.batchId,
                    c.stageId,
                    c.reportHash,
                    c.qaPassed,
                    c.deadline,
                    c.ipfsCid,
                    c.stationSignature,
                    c.approvalDeadline,
                    c.approvalSignature
                )
            );
            assertEq(encoded, _expected(i), _name(i));
        }
    }

    function test_FixtureCoversBoundaryShapes() public view {
        bool emptyApproval;
        bool rejection;
        bool maxCid;
        for (uint256 i = 0; i < CASE_COUNT; i++) {
            CallInput memory c = _input(i);
            assertEq(c.batchId, batchId, _name(i));
            assertEq(c.stationSignature.length, 65, _name(i));
            if (c.approvalSignature.length == 0) {
                emptyApproval = true;
                assertEq(c.approvalDeadline, 0, _name(i));
            } else {
                assertEq(c.approvalSignature.length, 65, _name(i));
            }
            if (!c.qaPassed) rejection = true;
            if (bytes(c.ipfsCid).length == sc.MAX_CID_LENGTH()) maxCid = true;
        }
        assertTrue(emptyApproval, "empty approval case");
        assertTrue(rejection, "rejection case");
        assertTrue(maxCid, "max CID case");
    }

    /// @dev Guards against drift: the approved QA case must be the shared signature vector.
    function test_ApprovedCaseIsSharedSignatureVector() public view {
        string memory vector = vm.readFile(VECTOR_PATH);
        CallInput memory c = _input(_indexOf("stage2-qa-approved"));

        assertEq(c.batchId, vm.parseJsonBytes32(vector, ".stageCompletion.message.batchId"));
        assertEq(c.stageId, vm.parseJsonUint(vector, ".stageCompletion.message.stageId"));
        assertEq(c.reportHash, vm.parseJsonBytes32(vector, ".reportHash"));
        assertEq(c.qaPassed, vm.parseJsonBool(vector, ".stageCompletion.message.qaPassed"));
        assertEq(c.deadline, vm.parseJsonUint(vector, ".stageCompletion.message.deadline"));
        assertEq(c.stationSignature, vm.parseJsonBytes(vector, ".stageCompletion.signature"));
        assertEq(c.approvalDeadline, vm.parseJsonUint(vector, ".stageApproval.message.deadline"));
        assertEq(c.approvalSignature, vm.parseJsonBytes(vector, ".stageApproval.signature"));
        assertEq(
            vm.parseJsonAddress(json, ".stationAddress"),
            vm.parseJsonAddress(vector, ".stageCompletion.signer")
        );
        assertEq(
            vm.parseJsonAddress(json, ".qaInspectorAddress"),
            vm.parseJsonAddress(vector, ".stageApproval.signer")
        );
    }

    /// @dev Replays the raw fixture bytes in order on one batch, as the relayer would send them.
    function test_RawCalldataExecutesInOrder() public {
        assertEq(vm.parseJsonAddress(json, ".stationAddress"), ANVIL_ADDR_1);
        assertEq(vm.parseJsonAddress(json, ".qaInspectorAddress"), ANVIL_ADDR_2);
        vm.startPrank(admin);
        sc.registerStation(
            ANVIL_ADDR_1, keccak256("ST-CALLDATA"), uint64(block.timestamp + 1 days), ALL_STAGES
        );
        sc.grantRole(sc.QA_INSPECTOR_ROLE(), ANVIL_ADDR_2);
        vm.stopPrank();

        uint32[CASE_COUNT] memory nextAfter = [uint32(1), 2, 2, 3, 4];
        for (uint256 i = 0; i < CASE_COUNT; i++) {
            CallInput memory c = _input(i);
            if (c.qaPassed) {
                vm.expectEmit(true, true, false, true, address(sc));
                emit SupplyChain.StageCompleted(
                    batchId,
                    c.stageId,
                    ANVIL_ADDR_1,
                    c.stageId == sc.QA_STAGE() ? ANVIL_ADDR_2 : address(0),
                    c.reportHash,
                    c.ipfsCid
                );
            } else {
                vm.expectEmit(true, true, false, true, address(sc));
                emit SupplyChain.StageRejected(
                    batchId, c.stageId, ANVIL_ADDR_1, c.reportHash, c.ipfsCid
                );
            }
            vm.prank(relayer);
            (bool ok, bytes memory ret) = address(sc).call(_expected(i));
            assertTrue(ok, string.concat(_name(i), ": reverted ", vm.toString(ret)));
            _assertBatch(nextAfter[i], nextAfter[i] == 4);
        }
    }

    // ------------------------------------------------------------ helpers

    function _case(uint256 i, string memory field) internal pure returns (string memory) {
        return string.concat(".cases[", vm.toString(i), "].", field);
    }

    function _name(uint256 i) internal view returns (string memory) {
        return vm.parseJsonString(json, _case(i, "name"));
    }

    function _expected(uint256 i) internal view returns (bytes memory) {
        return vm.parseJsonBytes(json, _case(i, "expectedCalldata"));
    }

    function _indexOf(string memory name) internal view returns (uint256) {
        for (uint256 i = 0; i < CASE_COUNT; i++) {
            if (keccak256(bytes(_name(i))) == keccak256(bytes(name))) return i;
        }
        revert(string.concat("missing case ", name));
    }

    function _input(uint256 i) internal view returns (CallInput memory c) {
        c.batchId = vm.parseJsonBytes32(json, _case(i, "input.batchId"));
        c.stageId = uint32(vm.parseJsonUint(json, _case(i, "input.stageId")));
        c.reportHash = vm.parseJsonBytes32(json, _case(i, "input.reportHash"));
        c.qaPassed = vm.parseJsonBool(json, _case(i, "input.qaPassed"));
        c.deadline = vm.parseJsonUint(json, _case(i, "input.deadline"));
        c.ipfsCid = vm.parseJsonString(json, _case(i, "input.ipfsCid"));
        c.stationSignature = vm.parseJsonBytes(json, _case(i, "input.stationSignature"));
        c.approvalDeadline = vm.parseJsonUint(json, _case(i, "input.approvalDeadline"));
        c.approvalSignature = vm.parseJsonBytes(json, _case(i, "input.approvalSignature"));
    }
}
