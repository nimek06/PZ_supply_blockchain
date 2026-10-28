// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Test} from "forge-std/Test.sol";
import {SupplyChain} from "../src/SupplyChain.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";

/// @dev Shared setup and independent EIP-712 helpers (the digest is NOT taken from the contract).
abstract contract SupplyChainBase is Test {
    /// @dev Fixed address agreed for the shared signature vector (first Anvil deployment address).
    address internal constant VECTOR_CONTRACT = 0x5FbDB2315678afecb367f032d93F642f64180aa3;
    uint256 internal constant WINDOW = 3600;
    uint32 internal constant ALL_STAGES = 0x0F;

    bytes32 internal constant DOMAIN_TYPEHASH = keccak256(
        "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
    );
    bytes32 internal constant COMPLETION_TYPEHASH = keccak256(
        "StageCompletion(bytes32 batchId,uint32 stageId,bytes32 reportHash,bool qaPassed,uint256 deadline)"
    );
    bytes32 internal constant APPROVAL_TYPEHASH = keccak256(
        "StageApproval(bytes32 batchId,uint32 stageId,bytes32 reportHash,uint256 deadline)"
    );

    string internal constant BATCH_NUMBER = "B-2026-0001";
    string internal constant CID = "bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy";

    SupplyChain internal sc;
    bytes32 internal batchId;

    address internal admin = makeAddr("admin");
    address internal relayer = makeAddr("relayer");
    address internal station;
    uint256 internal stationKey;
    address internal operator;
    uint256 internal operatorKey;

    function setUp() public virtual {
        vm.warp(1_800_000_000);
        (station, stationKey) = makeAddrAndKey("station");
        (operator, operatorKey) = makeAddrAndKey("operator");

        deployCodeTo("SupplyChain.sol:SupplyChain", abi.encode(admin, WINDOW), VECTOR_CONTRACT);
        sc = SupplyChain(VECTOR_CONTRACT);

        vm.startPrank(admin);
        sc.grantRole(sc.RELAYER_ROLE(), relayer);
        sc.grantRole(sc.QA_INSPECTOR_ROLE(), operator);
        sc.registerStation(
            station, keccak256("ST-001"), uint64(block.timestamp + 365 days), ALL_STAGES
        );
        vm.stopPrank();

        vm.prank(relayer);
        batchId = sc.createBatch(BATCH_NUMBER);
    }

    // ------------------------------------------------------------ helpers

    function _domain(address verifyingContract) internal view returns (bytes32) {
        return keccak256(
            abi.encode(
                DOMAIN_TYPEHASH,
                keccak256("SupplyChain"),
                keccak256("1"),
                block.chainid,
                verifyingContract
            )
        );
    }

    function _completionDigest(
        address verifyingContract,
        bytes32 b,
        uint32 s,
        bytes32 h,
        bool passed,
        uint256 deadline
    ) internal view returns (bytes32) {
        bytes32 structHash = keccak256(abi.encode(COMPLETION_TYPEHASH, b, s, h, passed, deadline));
        return keccak256(abi.encodePacked("\x19\x01", _domain(verifyingContract), structHash));
    }

    function _approvalDigest(
        address verifyingContract,
        bytes32 b,
        uint32 s,
        bytes32 h,
        uint256 deadline
    ) internal view returns (bytes32) {
        bytes32 structHash = keccak256(abi.encode(APPROVAL_TYPEHASH, b, s, h, deadline));
        return keccak256(abi.encodePacked("\x19\x01", _domain(verifyingContract), structHash));
    }

    function _sign(uint256 key, bytes32 digest) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, digest);
        return abi.encodePacked(r, s, v);
    }

    function _signCompletion(
        uint256 key,
        uint32 stageId,
        bytes32 reportHash,
        bool passed,
        uint256 deadline
    ) internal view returns (bytes memory) {
        return _sign(
            key, _completionDigest(address(sc), batchId, stageId, reportHash, passed, deadline)
        );
    }

    function _signApproval(uint256 key, uint32 stageId, bytes32 reportHash, uint256 deadline)
        internal
        view
        returns (bytes memory)
    {
        return _sign(key, _approvalDigest(address(sc), batchId, stageId, reportHash, deadline));
    }

    function _report(uint32 stageId) internal pure returns (bytes32) {
        return keccak256(abi.encodePacked("report", stageId));
    }

    function _deadline() internal view returns (uint256) {
        return block.timestamp + 600;
    }

    /// @dev Submits a valid, passing stage (with operator approval for the QA stage).
    function _completeValid(uint32 stageId) internal {
        uint256 deadline = _deadline();
        bytes32 h = _report(stageId);
        bytes memory sig = _signCompletion(stationKey, stageId, h, true, deadline);
        bytes memory approval =
            stageId == 2 ? _signApproval(operatorKey, stageId, h, deadline) : bytes("");
        vm.prank(relayer);
        sc.completeStage(batchId, stageId, h, true, deadline, CID, sig, deadline, approval);
    }

    function _submit(
        uint32 stageId,
        bytes32 h,
        bool passed,
        uint256 deadline,
        bytes memory sig,
        uint256 approvalDeadline,
        bytes memory approval
    ) internal {
        vm.prank(relayer);
        sc.completeStage(
            batchId, stageId, h, passed, deadline, CID, sig, approvalDeadline, approval
        );
    }

    function _assertBatch(uint32 expectedNext, bool expectedCompleted) internal view {
        (bool exists, uint32 nextStage, bool completed) = sc.getBatch(batchId);
        assertTrue(exists);
        assertEq(nextStage, expectedNext);
        assertEq(completed, expectedCompleted);
    }
}

contract SupplyChainTest is SupplyChainBase {
    // ------------------------------------------------------- deployment / domain

    function test_DomainMatchesProtocol() public view {
        assertEq(sc.domainSeparator(), _domain(VECTOR_CONTRACT));
        (, string memory name, string memory version, uint256 chainId, address verifying,,) =
            sc.eip712Domain();
        assertEq(name, "SupplyChain");
        assertEq(version, "1");
        assertEq(chainId, 31337);
        assertEq(verifying, VECTOR_CONTRACT);
        assertEq(sc.STAGE_COMPLETION_TYPEHASH(), COMPLETION_TYPEHASH);
        assertEq(sc.STAGE_APPROVAL_TYPEHASH(), APPROVAL_TYPEHASH);
        assertEq(sc.maxDeadlineWindow(), WINDOW);
        assertTrue(sc.hasRole(sc.DEFAULT_ADMIN_ROLE(), admin));
    }

    function test_HashHelpersMatchIndependentEncoding() public view {
        bytes32 h = _report(1);
        assertEq(
            sc.hashStageCompletion(batchId, 1, h, true, 123),
            _completionDigest(address(sc), batchId, 1, h, true, 123)
        );
        assertEq(
            sc.hashStageApproval(batchId, 2, h, 456),
            _approvalDigest(address(sc), batchId, 2, h, 456)
        );
    }

    function test_RevertWhen_ConstructorArgsInvalid() public {
        vm.expectRevert(SupplyChain.InvalidAddress.selector);
        new SupplyChain(address(0), WINDOW);
        vm.expectRevert(SupplyChain.InvalidDeadlineWindow.selector);
        new SupplyChain(admin, 0);
    }

    // --------------------------------------------------------------- createBatch

    function test_CreateBatch() public {
        bytes32 expected = keccak256(bytes("B-2026-0002"));
        vm.expectEmit(true, false, false, true, address(sc));
        emit SupplyChain.BatchCreated(expected, "B-2026-0002");
        vm.prank(relayer);
        bytes32 id = sc.createBatch("B-2026-0002");
        assertEq(id, expected);
        (bool exists, uint32 nextStage, bool completed) = sc.getBatch(id);
        assertTrue(exists);
        assertEq(nextStage, 0);
        assertFalse(completed);
    }

    function test_BatchIdIsKeccakOfUtf8Number() public view {
        assertEq(batchId, keccak256(bytes(BATCH_NUMBER)));
    }

    function test_RevertWhen_BatchDuplicated() public {
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.BatchAlreadyExists.selector, batchId));
        vm.prank(relayer);
        sc.createBatch(BATCH_NUMBER);
    }

    function test_RevertWhen_BatchNumberMalformed() public {
        string[8] memory bad = [
            "",
            "B-2026-001",
            "B-2026-00001",
            "b-2026-0001",
            "B_2026-0001",
            "B-2026_0001",
            "B-20A6-0001",
            "B-2026-000x"
        ];
        for (uint256 i = 0; i < bad.length; ++i) {
            vm.expectRevert(SupplyChain.InvalidBatchNumber.selector);
            vm.prank(relayer);
            sc.createBatch(bad[i]);
        }
    }

    function test_RevertWhen_CreateBatchNotRelayer() public {
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, admin, sc.RELAYER_ROLE()
            )
        );
        vm.prank(admin);
        sc.createBatch("B-2026-0003");
    }

    // ----------------------------------------------------------- station registry

    function test_RegisterAndRevokeStation() public {
        address s2 = makeAddr("s2");
        uint64 exp = uint64(block.timestamp + 1 days);
        vm.expectEmit(true, false, false, true, address(sc));
        emit SupplyChain.StationRegistered(s2, bytes32("ST-2"), exp, 0x03);
        vm.prank(admin);
        sc.registerStation(s2, bytes32("ST-2"), exp, 0x03);

        (bool active, bytes32 id, uint64 expiresAt, uint32 mask) = sc.getStation(s2);
        assertTrue(active);
        assertEq(id, bytes32("ST-2"));
        assertEq(expiresAt, exp);
        assertEq(mask, 0x03);

        vm.expectEmit(true, false, false, false, address(sc));
        emit SupplyChain.StationRevoked(s2);
        vm.prank(admin);
        sc.revokeStation(s2);
        (active,,,) = sc.getStation(s2);
        assertFalse(active);

        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, s2));
        vm.prank(admin);
        sc.revokeStation(s2);
    }

    function test_RevertWhen_StationRegistrationInvalid() public {
        uint64 exp = uint64(block.timestamp + 1 days);
        vm.startPrank(admin);
        vm.expectRevert(SupplyChain.InvalidAddress.selector);
        sc.registerStation(address(0), bytes32("X"), exp, 1);
        vm.expectRevert(SupplyChain.InvalidStationRegistration.selector);
        sc.registerStation(station, bytes32(0), exp, 1);
        vm.expectRevert(SupplyChain.InvalidStationRegistration.selector);
        sc.registerStation(station, bytes32("X"), uint64(block.timestamp), 1);
        vm.expectRevert(SupplyChain.InvalidStationRegistration.selector);
        sc.registerStation(station, bytes32("X"), exp, 0);
        vm.expectRevert(SupplyChain.InvalidStationRegistration.selector);
        sc.registerStation(station, bytes32("X"), exp, 0x10);
        vm.stopPrank();
    }

    function test_RevertWhen_RegistryCalledByNonAdmin() public {
        bytes memory err = abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, relayer, bytes32(0)
        );
        vm.startPrank(relayer);
        vm.expectRevert(err);
        sc.registerStation(relayer, bytes32("X"), uint64(block.timestamp + 1), 1);
        vm.expectRevert(err);
        sc.revokeStation(station);
        vm.expectRevert(err);
        sc.setMaxDeadlineWindow(10);
        vm.stopPrank();
    }

    function test_SetMaxDeadlineWindow() public {
        vm.expectEmit(false, false, false, true, address(sc));
        emit SupplyChain.MaxDeadlineWindowUpdated(WINDOW, 60);
        vm.prank(admin);
        sc.setMaxDeadlineWindow(60);
        assertEq(sc.maxDeadlineWindow(), 60);

        vm.expectRevert(SupplyChain.InvalidDeadlineWindow.selector);
        vm.prank(admin);
        sc.setMaxDeadlineWindow(0);
    }

    // ------------------------------------------------------------- happy path

    function test_FullRouteZeroToThree() public {
        for (uint32 stageId = 0; stageId < 4; ++stageId) {
            uint256 deadline = _deadline();
            bytes32 h = _report(stageId);
            bytes memory sig = _signCompletion(stationKey, stageId, h, true, deadline);
            bytes memory approval;
            address expectedOperator;
            if (stageId == 2) {
                approval = _signApproval(operatorKey, stageId, h, deadline);
                expectedOperator = operator;
            }
            vm.expectEmit(true, true, false, true, address(sc));
            emit SupplyChain.StageCompleted(batchId, stageId, station, expectedOperator, h, CID);
            _submit(stageId, h, true, deadline, sig, deadline, approval);
            _assertBatch(stageId + 1, stageId == 3);
        }
    }

    function test_RevertWhen_BatchAlreadyCompleted() public {
        for (uint32 i = 0; i < 4; ++i) {
            _completeValid(i);
        }
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 4, _report(4), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.BatchAlreadyCompleted.selector, batchId));
        _submit(4, _report(4), true, deadline, sig, 0, "");
    }

    // ------------------------------------------------------------ order / batch

    function test_RevertWhen_StageOutOfOrder() public {
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 1, _report(1), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.UnexpectedStage.selector, 0, 1));
        _submit(1, _report(1), true, deadline, sig, 0, "");
        _assertBatch(0, false);
    }

    function test_RevertWhen_UnknownBatch() public {
        bytes32 unknown = keccak256("B-2099-9999");
        uint256 deadline = _deadline();
        bytes memory sig = _sign(
            stationKey, _completionDigest(address(sc), unknown, 0, _report(0), true, deadline)
        );
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.UnknownBatch.selector, unknown));
        vm.prank(relayer);
        sc.completeStage(unknown, 0, _report(0), true, deadline, CID, sig, 0, "");
    }

    function test_RevertWhen_CompleteStageNotRelayer() public {
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        vm.expectRevert(
            abi.encodeWithSelector(
                IAccessControl.AccessControlUnauthorizedAccount.selector, station, sc.RELAYER_ROLE()
            )
        );
        vm.prank(station);
        sc.completeStage(batchId, 0, _report(0), true, deadline, CID, sig, 0, "");
    }

    function test_RevertWhen_ReplayAfterCompletion() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory sig = _signCompletion(stationKey, 0, h, true, deadline);
        _submit(0, h, true, deadline, sig, 0, "");
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.UnexpectedStage.selector, 1, 0));
        _submit(0, h, true, deadline, sig, 0, "");
        _assertBatch(1, false);
    }

    // ------------------------------------------------------------------ deadline

    function test_DeadlineAtCurrentBlockIsAccepted() public {
        uint256 deadline = block.timestamp;
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        _submit(0, _report(0), true, deadline, sig, 0, "");
        _assertBatch(1, false);
    }

    function test_RevertWhen_DeadlineExpired() public {
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        vm.warp(deadline + 1);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.DeadlineExpired.selector, deadline));
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    function test_RevertWhen_DeadlineBeyondWindow() public {
        uint256 deadline = block.timestamp + WINDOW + 1;
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        vm.expectRevert(
            abi.encodeWithSelector(
                SupplyChain.DeadlineTooFar.selector, deadline, block.timestamp + WINDOW
            )
        );
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    function test_DeadlineAtWindowEdgeIsAccepted() public {
        uint256 deadline = block.timestamp + WINDOW;
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        _submit(0, _report(0), true, deadline, sig, 0, "");
        _assertBatch(1, false);
    }

    // ------------------------------------------------------------------ station

    function test_RevertWhen_StationRevoked() public {
        vm.prank(admin);
        sc.revokeStation(station);
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, station));
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    function test_RevertWhen_StationExpired() public {
        uint64 exp = uint64(block.timestamp + 100);
        vm.prank(admin);
        sc.registerStation(station, keccak256("ST-001"), exp, ALL_STAGES);
        vm.warp(exp + 1);
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationExpired.selector, station, exp));
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    function test_RevertWhen_StationUnregistered() public {
        (address stranger, uint256 strangerKey) = makeAddrAndKey("stranger");
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(strangerKey, 0, _report(0), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, stranger));
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    function test_RevertWhen_StageOutsideStageMask() public {
        vm.prank(admin);
        sc.registerStation(station, keccak256("ST-001"), uint64(block.timestamp + 1 days), 0x0E);
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StageNotAllowed.selector, station, 0));
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    function test_RelayerCannotSignAsStation() public {
        // A relayer key is not a registered station, so it cannot authorize a stage itself.
        (address relayer2, uint256 relayer2Key) = makeAddrAndKey("relayer2");
        bytes32 relayerRole = sc.RELAYER_ROLE();
        vm.prank(admin);
        sc.grantRole(relayerRole, relayer2);
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(relayer2Key, 0, _report(0), true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, relayer2));
        _submit(0, _report(0), true, deadline, sig, 0, "");
    }

    // ------------------------------------------------------- signature binding

    function test_RevertWhen_SignedFieldMutated() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory sig = _signCompletion(stationKey, 0, h, true, deadline);

        // Different report hash, deadline or qaPassed recovers a different (unregistered) signer.
        vm.expectRevert();
        _submit(0, keccak256("other report"), true, deadline, sig, 0, "");
        vm.expectRevert();
        _submit(0, h, true, deadline - 1, sig, 0, "");
        vm.expectRevert();
        _submit(0, h, false, deadline, sig, 0, "");

        _assertBatch(0, false);
        _submit(0, h, true, deadline, sig, 0, "");
        _assertBatch(1, false);
    }

    function test_RevertWhen_SignedForOtherContract() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory sig =
            _sign(stationKey, _completionDigest(address(0xBEEF), batchId, 0, h, true, deadline));
        vm.expectRevert();
        _submit(0, h, true, deadline, sig, 0, "");
        _assertBatch(0, false);
    }

    function test_RevertWhen_SignedForOtherChain() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        vm.chainId(80002);
        bytes memory sig = _signCompletion(stationKey, 0, h, true, deadline);
        vm.chainId(31337);
        vm.expectRevert();
        _submit(0, h, true, deadline, sig, 0, "");
        _assertBatch(0, false);
    }

    function test_RevertWhen_ApprovalSignatureUsedAsCompletion() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory approvalAsStation = _signApproval(stationKey, 0, h, deadline);
        vm.expectRevert();
        _submit(0, h, true, deadline, approvalAsStation, 0, "");
    }

    function test_RevertWhen_SignatureMalformed() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        vm.expectRevert(SupplyChain.InvalidStationSignature.selector);
        _submit(0, h, true, deadline, "", 0, "");
        vm.expectRevert(SupplyChain.InvalidStationSignature.selector);
        _submit(0, h, true, deadline, new bytes(65), 0, "");
    }

    function test_RevertWhen_SignatureMalleable() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        (uint8 v, bytes32 r, bytes32 s) =
            vm.sign(stationKey, _completionDigest(address(sc), batchId, 0, h, true, deadline));
        uint256 n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141;
        bytes memory highS = abi.encodePacked(r, bytes32(n - uint256(s)), v == 27 ? uint8(28) : 27);
        vm.expectRevert(SupplyChain.InvalidStationSignature.selector);
        _submit(0, h, true, deadline, highS, 0, "");
    }

    function test_RevertWhen_ReportFieldsInvalid() public {
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, bytes32(0), true, deadline);
        vm.expectRevert(SupplyChain.InvalidReportHash.selector);
        _submit(0, bytes32(0), true, deadline, sig, 0, "");

        bytes32 h = _report(0);
        sig = _signCompletion(stationKey, 0, h, true, deadline);
        vm.prank(relayer);
        vm.expectRevert(SupplyChain.InvalidCid.selector);
        sc.completeStage(batchId, 0, h, true, deadline, "", sig, 0, "");

        bytes memory longCid = new bytes(129);
        vm.prank(relayer);
        vm.expectRevert(SupplyChain.InvalidCid.selector);
        sc.completeStage(batchId, 0, h, true, deadline, string(longCid), sig, 0, "");
    }

    // ------------------------------------------------------------- rejection

    function test_QaFailedEmitsRejectionWithoutStateChange() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory sig = _signCompletion(stationKey, 0, h, false, deadline);
        vm.expectEmit(true, true, false, true, address(sc));
        emit SupplyChain.StageRejected(batchId, 0, station, h, CID);
        _submit(0, h, false, deadline, sig, 0, "");
        _assertBatch(0, false);
        assertTrue(
            sc.rejectionRecorded(_completionDigest(address(sc), batchId, 0, h, false, deadline))
        );

        vm.expectRevert();
        _submit(0, h, false, deadline, sig, 0, "");

        // After a rejection the station may submit a new (passing) report for the same stage.
        _completeValid(0);
        _assertBatch(1, false);
    }

    function test_QaFailedAtControlStageNeedsNoApproval() public {
        _completeValid(0);
        _completeValid(1);
        uint256 deadline = _deadline();
        bytes32 h = _report(2);
        bytes memory sig = _signCompletion(stationKey, 2, h, false, deadline);
        vm.expectEmit(true, true, false, true, address(sc));
        emit SupplyChain.StageRejected(batchId, 2, station, h, CID);
        _submit(2, h, false, deadline, sig, 0, "");
        _assertBatch(2, false);
    }

    function test_RevertWhen_RejectionFromUnauthorizedStation() public {
        vm.prank(admin);
        sc.revokeStation(station);
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(stationKey, 0, _report(0), false, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, station));
        _submit(0, _report(0), false, deadline, sig, 0, "");
    }

    // ----------------------------------------------------- quality control (2 keys)

    function _toControlStage() internal returns (uint256 deadline, bytes32 h, bytes memory sig) {
        _completeValid(0);
        _completeValid(1);
        deadline = _deadline();
        h = _report(2);
        sig = _signCompletion(stationKey, 2, h, true, deadline);
    }

    function test_RevertWhen_ControlWithoutApproval() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        vm.expectRevert(SupplyChain.MissingApproval.selector);
        _submit(2, h, true, deadline, sig, deadline, "");
        _assertBatch(2, false);
    }

    function test_RevertWhen_ControlApprovalExpired() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        uint256 approvalDeadline = block.timestamp + 10;
        bytes memory approval = _signApproval(operatorKey, 2, h, approvalDeadline);
        vm.warp(approvalDeadline + 1);
        vm.expectRevert(
            abi.encodeWithSelector(SupplyChain.ApprovalExpired.selector, approvalDeadline)
        );
        _submit(2, h, true, deadline, sig, approvalDeadline, approval);
    }

    function test_RevertWhen_ControlApprovalForOtherReport() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        bytes memory approval = _signApproval(operatorKey, 2, keccak256("other"), deadline);
        vm.expectRevert();
        _submit(2, h, true, deadline, sig, deadline, approval);
        _assertBatch(2, false);
    }

    function test_RevertWhen_ControlApprovalDeadlineChanged() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        bytes memory approval = _signApproval(operatorKey, 2, h, deadline);
        vm.expectRevert();
        _submit(2, h, true, deadline, sig, deadline - 1, approval);
    }

    function test_RevertWhen_ControlApprovedByNonInspector() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        (address other, uint256 otherKey) = makeAddrAndKey("not-qa");
        bytes memory approval = _signApproval(otherKey, 2, h, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.OperatorNotQaInspector.selector, other));
        _submit(2, h, true, deadline, sig, deadline, approval);
    }

    function test_RevertWhen_ControlStationAndOperatorSameAddress() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        bytes32 qaRole = sc.QA_INSPECTOR_ROLE();
        vm.prank(admin);
        sc.grantRole(qaRole, station);
        bytes memory approval = _signApproval(stationKey, 2, h, deadline);
        vm.expectRevert(
            abi.encodeWithSelector(SupplyChain.SameStationAndOperator.selector, station)
        );
        _submit(2, h, true, deadline, sig, deadline, approval);
        _assertBatch(2, false);
    }

    function test_RevertWhen_ControlOperatorRoleRevoked() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        bytes memory approval = _signApproval(operatorKey, 2, h, deadline);
        bytes32 qaRole = sc.QA_INSPECTOR_ROLE();
        vm.prank(admin);
        sc.revokeRole(qaRole, operator);
        vm.expectRevert(
            abi.encodeWithSelector(SupplyChain.OperatorNotQaInspector.selector, operator)
        );
        _submit(2, h, true, deadline, sig, deadline, approval);

        // Re-granting the role is valid only while the approval itself has not expired.
        vm.prank(admin);
        sc.grantRole(qaRole, operator);
        vm.warp(deadline + 1);
        vm.expectRevert();
        _submit(2, h, true, deadline, sig, deadline, approval);
    }

    function test_RevertWhen_ControlOnlyOperatorSigned() public {
        (uint256 deadline, bytes32 h,) = _toControlStage();
        bytes memory approval = _signApproval(operatorKey, 2, h, deadline);
        // The operator's key cannot stand in for the station signature.
        bytes memory operatorAsStation = _signCompletion(operatorKey, 2, h, true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, operator));
        _submit(2, h, true, deadline, operatorAsStation, deadline, approval);
    }

    function test_RevertWhen_ControlApprovalMalformed() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        vm.expectRevert(SupplyChain.InvalidApprovalSignature.selector);
        _submit(2, h, true, deadline, sig, deadline, new bytes(65));
    }

    function test_ControlStageEmitsOperator() public {
        (uint256 deadline, bytes32 h, bytes memory sig) = _toControlStage();
        uint256 approvalDeadline = deadline - 60;
        bytes memory approval = _signApproval(operatorKey, 2, h, approvalDeadline);
        vm.expectEmit(true, true, false, true, address(sc));
        emit SupplyChain.StageCompleted(batchId, 2, station, operator, h, CID);
        _submit(2, h, true, deadline, sig, approvalDeadline, approval);
        _assertBatch(3, false);
    }

    function test_NonControlStageIgnoresApprovalFields() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory sig = _signCompletion(stationKey, 0, h, true, deadline);
        vm.expectEmit(true, true, false, true, address(sc));
        emit SupplyChain.StageCompleted(batchId, 0, station, address(0), h, CID);
        _submit(0, h, true, deadline, sig, 0, hex"1234");
    }

    // ------------------------------------------------------------------- fuzz

    function testFuzz_RevertWhen_WrongStage(uint32 stageId) public {
        vm.assume(stageId != 0);
        uint256 deadline = _deadline();
        bytes32 h = _report(stageId);
        bytes memory sig = _signCompletion(stationKey, stageId, h, true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.UnexpectedStage.selector, 0, stageId));
        _submit(stageId, h, true, deadline, sig, 0, "");
    }

    function testFuzz_DeadlineWindow(uint256 deadline) public {
        bytes32 h = _report(0);
        bytes memory sig = _signCompletion(stationKey, 0, h, true, deadline);
        bool valid = deadline >= block.timestamp && deadline <= block.timestamp + WINDOW;
        if (!valid) vm.expectRevert();
        _submit(0, h, true, deadline, sig, 0, "");
        _assertBatch(valid ? 1 : 0, false);
    }

    function testFuzz_RevertWhen_UnregisteredSigner(uint256 key, bytes32 h) public {
        key = bound(key, 1, 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140);
        vm.assume(key != stationKey && h != bytes32(0));
        uint256 deadline = _deadline();
        bytes memory sig = _signCompletion(key, 0, h, true, deadline);
        vm.expectRevert(abi.encodeWithSelector(SupplyChain.StationNotActive.selector, vm.addr(key)));
        _submit(0, h, true, deadline, sig, 0, "");
        _assertBatch(0, false);
    }

    function testFuzz_BatchIdFromNumber(uint16 year, uint16 serial) public {
        year = uint16(bound(year, 1000, 9999));
        serial = uint16(bound(serial, 0, 9999));
        string memory number = string.concat("B-", vm.toString(year), "-", _pad4(serial));
        vm.assume(keccak256(bytes(number)) != batchId);
        vm.prank(relayer);
        bytes32 id = sc.createBatch(number);
        assertEq(id, keccak256(bytes(number)));
    }

    function _pad4(uint16 n) internal pure returns (string memory) {
        bytes memory out = new bytes(4);
        for (uint256 i = 4; i > 0; --i) {
            out[i - 1] = bytes1(uint8(48 + n % 10));
            n /= 10;
        }
        return string(out);
    }
}
