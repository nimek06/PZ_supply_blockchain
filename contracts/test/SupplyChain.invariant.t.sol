// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Test} from "forge-std/Test.sol";
import {CommonBase} from "forge-std/Base.sol";
import {StdCheats} from "forge-std/StdCheats.sol";
import {StdUtils} from "forge-std/StdUtils.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {SupplyChain} from "../src/SupplyChain.sol";

/// @dev Actor of the invariant campaign: authorized completions and rejections, replays,
/// arbitrary calldata and forged authorizations. Every forged call keeps all other fields valid,
/// so it has to revert on the one check it targets. Ghost state advances only on authorized
/// completions; EIP-712 digests are computed independently of the contract.
contract SupplyChainHandler is CommonBase, StdCheats, StdUtils {
    enum Attack {
        StrangerSignsAsStation,
        OperatorSignsAsStation,
        RevokedStation,
        ExpiredStation,
        StageOutsideMask,
        SignedFieldMutated,
        RejectionReusedAsPass,
        SignedForOtherBatch,
        SignedForOtherContract,
        SignedForOtherChain,
        MalformedStationSignature,
        DeadlineExpired,
        DeadlineBeyondWindow,
        SubmittedWithoutRelayer,
        StrangerRecordsRejection,
        // QA stage only from here on
        MissingApproval,
        ApprovalByNonInspector,
        ApprovalFieldMutated,
        ApprovalByRevokedOperator,
        ApprovalBySameAccount,
        ApprovalExpired,
        MalformedApproval
    }

    enum Need {
        AnyStage,
        LaterStage,
        QaStage
    }

    struct Call {
        bytes32 batchId;
        uint32 stageId;
        bytes32 reportHash;
        bool qaPassed;
        uint256 deadline;
        bytes signature;
        uint256 approvalDeadline;
        bytes approval;
    }

    uint256 internal constant WINDOW = 3600;
    uint32 internal constant QA_STAGE = 2;
    uint256 internal constant MAX_BATCHES = 8;
    uint256 internal constant SECP256K1_N =
        0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141;
    string internal constant CID = "bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy";
    bytes32 internal constant DOMAIN_TYPEHASH = keccak256(
        "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
    );
    bytes32 internal constant COMPLETION_TYPEHASH = keccak256(
        "StageCompletion(bytes32 batchId,uint32 stageId,bytes32 reportHash,bool qaPassed,uint256 deadline)"
    );
    bytes32 internal constant APPROVAL_TYPEHASH = keccak256(
        "StageApproval(bytes32 batchId,uint32 stageId,bytes32 reportHash,uint256 deadline)"
    );

    SupplyChain public sc;

    address internal admin;
    uint256 internal adminKey;
    address internal relayer;
    uint256 internal relayerKey;
    address internal station;
    uint256 internal stationKey;
    /// @dev Registered station that also holds QA_INSPECTOR_ROLE.
    uint256 internal dualKey;
    /// @dev stageMask 0x01: stage 0 only.
    uint256 internal limitedKey;
    uint256 internal expiringKey;
    uint64 internal expiringUntil;
    uint256 internal revokedKey;
    address internal operatorA;
    uint256 internal operatorAKey;
    /// @dev QA_INSPECTOR_ROLE toggled during the campaign.
    address internal operatorB;
    uint256 internal operatorBKey;
    bool internal operatorBActive;

    uint256 public currentTime;
    uint256 internal reportNonce;
    bytes32[] internal batches;
    Call[] internal accepted;
    mapping(bytes32 batchId => uint32) public ghostNextStage;

    uint256 public calls;
    uint256 public authorizedCompletions;
    uint256 public authorizedRejections;
    uint256 public authorizedFailures;
    uint256 public attacks;
    uint256 public replays;
    uint256 public arbitraryCalls;
    uint256 public forgedSuccesses;
    uint256 public wrongReverts;
    string public lastFinding;
    mapping(Attack => uint256) public attacksByKind;

    constructor() {
        currentTime = block.timestamp;
        (admin, adminKey) = makeAddrAndKey("inv-admin");
        (relayer, relayerKey) = makeAddrAndKey("inv-relayer");
        (station, stationKey) = makeAddrAndKey("inv-station");
        (operatorA, operatorAKey) = makeAddrAndKey("inv-operator-a");
        (operatorB, operatorBKey) = makeAddrAndKey("inv-operator-b");
        address dualStation;
        address limitedStation;
        address expiringStation;
        address revokedStation;
        (dualStation, dualKey) = makeAddrAndKey("inv-dual-station");
        (limitedStation, limitedKey) = makeAddrAndKey("inv-limited-station");
        (expiringStation, expiringKey) = makeAddrAndKey("inv-expiring-station");
        (revokedStation, revokedKey) = makeAddrAndKey("inv-revoked-station");

        SupplyChain chain = new SupplyChain(admin, WINDOW);
        sc = chain;
        uint64 inOneYear = uint64(block.timestamp + 365 days);
        expiringUntil = uint64(block.timestamp + 1 days);
        bytes32 qaRole = chain.QA_INSPECTOR_ROLE();
        bytes32 relayerRole = chain.RELAYER_ROLE();

        vm.startPrank(admin);
        chain.grantRole(relayerRole, relayer);
        chain.grantRole(qaRole, operatorA);
        chain.grantRole(qaRole, operatorB);
        chain.grantRole(qaRole, dualStation);
        chain.registerStation(station, keccak256("ST-001"), inOneYear, 0x0F);
        chain.registerStation(dualStation, keccak256("ST-002"), inOneYear, 0x0F);
        chain.registerStation(limitedStation, keccak256("ST-003"), inOneYear, 0x01);
        chain.registerStation(expiringStation, keccak256("ST-004"), expiringUntil, 0x0F);
        chain.registerStation(revokedStation, keccak256("ST-005"), inOneYear, 0x0F);
        chain.revokeStation(revokedStation);
        vm.stopPrank();
        operatorBActive = true;

        _createBatch();
        _createBatch();
    }

    modifier atCurrentTime() {
        calls++;
        vm.warp(currentTime);
        _;
    }

    // ------------------------------------------------------------- actions

    function createBatch() external atCurrentTime {
        _createBatch();
    }

    function completeAuthorized(uint256 batchSeed, uint256 signerSeed, uint256 timeSeed)
        external
        atCurrentTime
    {
        (bool found, bytes32 b, uint32 stage) = _openBatch(batchSeed, Need.AnyStage);
        if (!found) return;
        uint256 key = _authorizedStationKey(stage, signerSeed);
        Call memory c = _call(b, stage, true, currentTime + _bound(timeSeed, 0, WINDOW));
        c.signature = _signCompletion(key, c, address(sc), block.chainid);
        if (stage == QA_STAGE) {
            c.approvalDeadline = currentTime + _bound(timeSeed >> 128, 0, 2 * WINDOW);
            c.approval = _signApproval(_authorizedApproverKey(key, signerSeed >> 128), c);
        }
        (bool ok, bytes memory reason) = _submit(relayer, c);
        if (!ok) {
            authorizedFailures++;
            _finding("authorized completion reverted", reason);
            return;
        }
        ghostNextStage[b] = stage + 1;
        authorizedCompletions++;
        accepted.push(c);
    }

    /// @dev `qaPassed == false` records StageRejected and must leave the batch where it was;
    /// approval fields are ignored, so they get arbitrary bytes.
    function rejectAuthorized(
        uint256 batchSeed,
        uint256 signerSeed,
        uint256 timeSeed,
        bytes calldata approvalNoise
    ) external atCurrentTime {
        (bool found, bytes32 b, uint32 stage) = _openBatch(batchSeed, Need.AnyStage);
        if (!found) return;
        Call memory c = _call(b, stage, false, currentTime + _bound(timeSeed, 0, WINDOW));
        c.signature = _signCompletion(
            _authorizedStationKey(stage, signerSeed), c, address(sc), block.chainid
        );
        c.approvalDeadline = timeSeed;
        c.approval = approvalNoise;
        (bool ok, bytes memory reason) = _submit(relayer, c);
        if (!ok) {
            authorizedFailures++;
            _finding("authorized rejection reverted", reason);
            return;
        }
        authorizedRejections++;
        replays++;
        (ok, reason) = _submit(relayer, c);
        _expectRevert(ok, reason, SupplyChain.RejectionAlreadyRecorded.selector, "rejection replay");
    }

    function attack(uint256 batchSeed, uint256 attackSeed, uint256 seed) external atCurrentTime {
        Attack a = Attack(_bound(attackSeed, 0, uint256(type(Attack).max)));
        Need need = a >= Attack.MissingApproval
            ? Need.QaStage
            : (a == Attack.StageOutsideMask ? Need.LaterStage : Need.AnyStage);
        (bool found, bytes32 b, uint32 stage) = _openBatch(batchSeed, need);
        if (!found) return;
        (Call memory c, address sender, bytes4 expected, bool applicable) =
            _forge(a, b, stage, seed);
        if (!applicable) return;
        attacks++;
        attacksByKind[a]++;
        (bool ok, bytes memory reason) = _submit(sender, c);
        _expectRevert(ok, reason, expected, string.concat("attack ", vm.toString(uint256(a))));
    }

    function replayAccepted(uint256 seed) external atCurrentTime {
        if (accepted.length == 0) return;
        Call memory c = accepted[seed % accepted.length];
        replays++;
        (bool ok, bytes memory reason) = _submit(relayer, c);
        (, uint32 next, bool completed) = sc.getBatch(c.batchId);
        bytes4 expected = completed
            ? SupplyChain.BatchAlreadyCompleted.selector
            : SupplyChain.UnexpectedStage.selector;
        _expectRevert(
            ok, reason, expected, string.concat("replay, nextStage ", vm.toString(uint256(next)))
        );
    }

    /// @dev Fuzzer-chosen signature and approval bytes for the next stage of an open batch.
    function submitArbitrary(
        uint256 batchSeed,
        bytes32 reportHash,
        bool qaPassed,
        uint256 timeSeed,
        bytes calldata signature,
        uint256 approvalDeadline,
        bytes calldata approval
    ) external atCurrentTime {
        (bool found, bytes32 b, uint32 stage) = _openBatch(batchSeed, Need.AnyStage);
        if (!found) return;
        Call memory c = _call(b, stage, qaPassed, currentTime + _bound(timeSeed, 0, WINDOW));
        if (reportHash != bytes32(0)) c.reportHash = reportHash;
        c.signature = signature;
        c.approvalDeadline = approvalDeadline;
        c.approval = approval;
        arbitraryCalls++;
        (bool ok,) = _submit(relayer, c);
        if (ok) {
            forgedSuccesses++;
            _finding("arbitrary signature accepted", "");
        }
    }

    function toggleOperatorB() external atCurrentTime {
        bytes32 role = sc.QA_INSPECTOR_ROLE();
        vm.prank(admin);
        if (operatorBActive) sc.revokeRole(role, operatorB);
        else sc.grantRole(role, operatorB);
        operatorBActive = !operatorBActive;
    }

    function warp(uint256 secondsSeed) external {
        calls++;
        currentTime += _bound(secondsSeed, 1, 12 hours);
        vm.warp(currentTime);
    }

    // --------------------------------------------------------------- views

    function batchCount() external view returns (uint256) {
        return batches.length;
    }

    function batchAt(uint256 i) external view returns (bytes32) {
        return batches[i];
    }

    // ------------------------------------------------------------ attacks

    function _forge(Attack a, bytes32 b, uint32 stage, uint256 seed)
        internal
        returns (Call memory c, address sender, bytes4 expected, bool applicable)
    {
        sender = relayer;
        applicable = true;
        expected = SupplyChain.StationNotActive.selector;
        c = _call(b, stage, true, currentTime + 600);
        if (stage == QA_STAGE) c.approvalDeadline = currentTime + 600;

        if (a == Attack.StrangerSignsAsStation) {
            uint256 pick = seed % 3;
            _signAs(c, pick == 0 ? _strangerKey(seed) : pick == 1 ? relayerKey : adminKey);
        } else if (a == Attack.OperatorSignsAsStation) {
            _signAs(c, seed % 2 == 0 ? operatorAKey : operatorBKey);
        } else if (a == Attack.RevokedStation) {
            _signAs(c, revokedKey);
        } else if (a == Attack.ExpiredStation) {
            applicable = currentTime > expiringUntil;
            _signAs(c, expiringKey);
            expected = SupplyChain.StationExpired.selector;
        } else if (a == Attack.StageOutsideMask) {
            _signAs(c, limitedKey);
            expected = SupplyChain.StageNotAllowed.selector;
        } else if (a == Attack.SignedFieldMutated) {
            _signAs(c, stationKey);
            if (seed % 2 == 0) c.reportHash = keccak256(abi.encode(c.reportHash));
            else c.deadline -= 1;
        } else if (a == Attack.RejectionReusedAsPass) {
            c.qaPassed = false;
            _signAs(c, stationKey);
            c.qaPassed = true;
        } else if (a == Attack.SignedForOtherBatch) {
            c.batchId = keccak256(abi.encode("other batch", seed));
            _signAs(c, stationKey);
            c.batchId = b;
        } else if (a == Attack.SignedForOtherContract) {
            address other = address(uint160(uint256(keccak256(abi.encode("other contract", seed)))));
            c.signature = _signCompletion(stationKey, c, other, block.chainid);
        } else if (a == Attack.SignedForOtherChain) {
            c.signature = _signCompletion(stationKey, c, address(sc), seed % 2 == 0 ? 80002 : 137);
        } else if (a == Attack.MalformedStationSignature) {
            _signAs(c, stationKey);
            c.signature = _malformed(c.signature, seed, true);
            expected = SupplyChain.InvalidStationSignature.selector;
        } else if (a == Attack.DeadlineExpired) {
            c.deadline = currentTime - 1 - seed % 1 days;
            _signAs(c, stationKey);
            expected = SupplyChain.DeadlineExpired.selector;
        } else if (a == Attack.DeadlineBeyondWindow) {
            c.deadline = currentTime + WINDOW + 1 + seed % 1 days;
            _signAs(c, stationKey);
            expected = SupplyChain.DeadlineTooFar.selector;
        } else if (a == Attack.SubmittedWithoutRelayer) {
            _signAs(c, stationKey);
            uint256 pick = seed % 3;
            sender = pick == 0 ? station : pick == 1 ? operatorA : admin;
            expected = IAccessControl.AccessControlUnauthorizedAccount.selector;
        } else if (a == Attack.StrangerRecordsRejection) {
            c.qaPassed = false;
            _signAs(c, _strangerKey(seed));
        } else {
            _forgeApproval(a, c, seed);
            expected = _approvalError(a);
            applicable = a != Attack.ApprovalByRevokedOperator || !operatorBActive;
        }
    }

    /// @dev QA stage: the station signature is valid, only the approval is wrong.
    function _forgeApproval(Attack a, Call memory c, uint256 seed) internal view {
        _signAs(c, a == Attack.ApprovalBySameAccount ? dualKey : stationKey);
        if (a == Attack.MissingApproval) {
            c.approval = "";
        } else if (a == Attack.ApprovalByNonInspector) {
            uint256 pick = seed % 3;
            c.approval = _signApproval(
                pick == 0 ? _strangerKey(seed) : pick == 1 ? stationKey : relayerKey, c
            );
        } else if (a == Attack.ApprovalFieldMutated) {
            c.approval = _signApproval(operatorAKey, c);
            if (seed % 2 == 0) c.approvalDeadline += 1;
            else c.approval = _signApproval(operatorAKey, _withReport(c, keccak256("other")));
        } else if (a == Attack.ApprovalByRevokedOperator) {
            c.approval = _signApproval(operatorBKey, c);
        } else if (a == Attack.ApprovalBySameAccount) {
            c.approval = _signApproval(dualKey, c);
        } else if (a == Attack.ApprovalExpired) {
            c.approvalDeadline = currentTime - 1 - seed % 1 days;
            c.approval = _signApproval(operatorAKey, c);
        } else {
            c.approval = _malformed(_signApproval(operatorAKey, c), seed, false);
        }
    }

    function _approvalError(Attack a) internal pure returns (bytes4) {
        if (a == Attack.MissingApproval) return SupplyChain.MissingApproval.selector;
        if (a == Attack.ApprovalBySameAccount) return SupplyChain.SameStationAndOperator.selector;
        if (a == Attack.ApprovalExpired) return SupplyChain.ApprovalExpired.selector;
        if (a == Attack.MalformedApproval) return SupplyChain.InvalidApprovalSignature.selector;
        return SupplyChain.OperatorNotQaInspector.selector;
    }

    /// @dev Empty (stations only), truncated, extended, all-zero, high-s and invalid-v variants.
    function _malformed(bytes memory sig, uint256 seed, bool allowEmpty)
        internal
        pure
        returns (bytes memory)
    {
        bytes32 r;
        bytes32 s;
        assembly ("memory-safe") {
            r := mload(add(sig, 0x20))
            s := mload(add(sig, 0x40))
        }
        uint8 v = uint8(sig[64]);
        uint256 variant = seed % (allowEmpty ? 6 : 5);
        if (variant == 0) return abi.encodePacked(r, s);
        if (variant == 1) return abi.encodePacked(sig, uint8(0));
        if (variant == 2) return new bytes(65);
        if (variant == 3) {
            return abi.encodePacked(r, bytes32(SECP256K1_N - uint256(s)), v == 27 ? uint8(28) : 27);
        }
        if (variant == 4) return abi.encodePacked(r, s, uint8(29));
        return "";
    }

    // ------------------------------------------------------------ helpers

    function _createBatch() internal {
        if (batches.length >= MAX_BATCHES) return;
        string memory number = string.concat("B-2027-", vm.toString(1001 + batches.length));
        vm.prank(relayer);
        try sc.createBatch(number) returns (bytes32 b) {
            batches.push(b);
        } catch (bytes memory reason) {
            authorizedFailures++;
            _finding("createBatch reverted", reason);
        }
    }

    function _openBatch(uint256 seed, Need need)
        internal
        view
        returns (bool found, bytes32 b, uint32 stage)
    {
        uint256 n = batches.length;
        for (uint256 i; i < n; ++i) {
            b = batches[(seed % n + i) % n];
            (, uint32 next, bool completed) = sc.getBatch(b);
            if (completed) continue;
            if (need == Need.QaStage && next != QA_STAGE) continue;
            if (need == Need.LaterStage && next == 0) continue;
            return (true, b, next);
        }
        return (false, bytes32(0), 0);
    }

    function _authorizedStationKey(uint32 stage, uint256 seed) internal view returns (uint256) {
        uint256 pick = seed % 4;
        if (pick == 1) return dualKey;
        if (pick == 2 && stage == 0) return limitedKey;
        if (pick == 3 && currentTime <= expiringUntil) return expiringKey;
        return stationKey;
    }

    function _authorizedApproverKey(uint256 signerKey, uint256 seed)
        internal
        view
        returns (uint256)
    {
        uint256 pick = seed % 3;
        if (pick == 1 && operatorBActive) return operatorBKey;
        if (pick == 2 && signerKey != dualKey) return dualKey;
        return operatorAKey;
    }

    function _call(bytes32 b, uint32 stage, bool qaPassed, uint256 deadline)
        internal
        returns (Call memory c)
    {
        c.batchId = b;
        c.stageId = stage;
        c.reportHash = keccak256(abi.encode("report", ++reportNonce));
        c.qaPassed = qaPassed;
        c.deadline = deadline;
    }

    function _withReport(Call memory c, bytes32 reportHash) internal pure returns (Call memory) {
        return Call(
            c.batchId,
            c.stageId,
            reportHash,
            c.qaPassed,
            c.deadline,
            c.signature,
            c.approvalDeadline,
            c.approval
        );
    }

    function _submit(address sender, Call memory c) internal returns (bool, bytes memory) {
        vm.prank(sender);
        try sc.completeStage(
            c.batchId,
            c.stageId,
            c.reportHash,
            c.qaPassed,
            c.deadline,
            CID,
            c.signature,
            c.approvalDeadline,
            c.approval
        ) {
            return (true, "");
        } catch (bytes memory reason) {
            return (false, reason);
        }
    }

    function _expectRevert(bool ok, bytes memory reason, bytes4 expected, string memory context)
        internal
    {
        if (ok) {
            forgedSuccesses++;
            _finding(string.concat(context, " succeeded"), "");
        } else if (bytes4(reason) != expected) {
            wrongReverts++;
            _finding(string.concat(context, " reverted with another error"), reason);
        }
    }

    function _finding(string memory context, bytes memory reason) internal {
        lastFinding = string.concat(context, ": ", vm.toString(reason));
    }

    function _signAs(Call memory c, uint256 key) internal view {
        c.signature = _signCompletion(key, c, address(sc), block.chainid);
    }

    function _strangerKey(uint256 seed) internal pure returns (uint256) {
        return uint256(keccak256(abi.encode("stranger", seed))) % (SECP256K1_N - 1) + 1;
    }

    function _signCompletion(uint256 key, Call memory c, address verifyingContract, uint256 chainId)
        internal
        pure
        returns (bytes memory)
    {
        bytes32 structHash = keccak256(
            abi.encode(
                COMPLETION_TYPEHASH, c.batchId, c.stageId, c.reportHash, c.qaPassed, c.deadline
            )
        );
        return _sign(key, _digest(verifyingContract, chainId, structHash));
    }

    function _signApproval(uint256 key, Call memory c) internal view returns (bytes memory) {
        bytes32 structHash = keccak256(
            abi.encode(APPROVAL_TYPEHASH, c.batchId, c.stageId, c.reportHash, c.approvalDeadline)
        );
        return _sign(key, _digest(address(sc), block.chainid, structHash));
    }

    function _digest(address verifyingContract, uint256 chainId, bytes32 structHash)
        internal
        pure
        returns (bytes32)
    {
        bytes32 domain = keccak256(
            abi.encode(
                DOMAIN_TYPEHASH,
                keccak256("SupplyChain"),
                keccak256("1"),
                chainId,
                verifyingContract
            )
        );
        return keccak256(abi.encodePacked("\x19\x01", domain, structHash));
    }

    function _sign(uint256 key, bytes32 digest) internal pure returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, digest);
        return abi.encodePacked(r, s, v);
    }
}

/// @notice Invariant (plan tyg. 2): partia nie przesuwa się bez poprawnych podpisów. `nextStage`
/// każdej partii równa się liczbie autoryzowanych ukończeń, a żadne wywołanie sfałszowane,
/// powtórzone ani z dowolnymi bajtami podpisu nie przechodzi.
/// forge-config: default.invariant.runs = 64
/// forge-config: default.invariant.depth = 128
/// forge-config: default.invariant.fail-on-revert = true
contract SupplyChainInvariantTest is Test {
    /// @dev Shrunk and replayed counterexamples are shorter, so the vacuity guard skips them.
    uint256 internal constant MIN_CALLS_FOR_COVERAGE_GUARD = 64;

    SupplyChainHandler internal handler;
    SupplyChain internal sc;

    function setUp() public {
        vm.warp(1_800_000_000);
        handler = new SupplyChainHandler();
        sc = handler.sc();

        bytes4[] memory selectors = new bytes4[](8);
        selectors[0] = SupplyChainHandler.createBatch.selector;
        selectors[1] = SupplyChainHandler.completeAuthorized.selector;
        selectors[2] = SupplyChainHandler.rejectAuthorized.selector;
        selectors[3] = SupplyChainHandler.attack.selector;
        selectors[4] = SupplyChainHandler.replayAccepted.selector;
        selectors[5] = SupplyChainHandler.submitArbitrary.selector;
        selectors[6] = SupplyChainHandler.toggleOperatorB.selector;
        selectors[7] = SupplyChainHandler.warp.selector;
        targetSelector(FuzzSelector({addr: address(handler), selectors: selectors}));
        targetContract(address(handler));
    }

    function invariant_BatchAdvancesOnlyWithAuthorizedSignatures() public view {
        uint32 stageCount = sc.STAGE_COUNT();
        for (uint256 i; i < handler.batchCount(); ++i) {
            bytes32 b = handler.batchAt(i);
            (bool exists, uint32 nextStage, bool completed) = sc.getBatch(b);
            assertTrue(exists, "batch disappeared");
            assertEq(nextStage, handler.ghostNextStage(b), "nextStage != authorized completions");
            assertLe(nextStage, stageCount, "nextStage beyond the route");
            assertEq(completed, nextStage == stageCount, "completed flag out of sync");
        }
    }

    function invariant_ForgedReplayedAndArbitraryCallsNeverSucceed() public view {
        assertEq(handler.forgedSuccesses(), 0, handler.lastFinding());
    }

    function invariant_ForgedCallsRevertOnTheTargetedCheck() public view {
        assertEq(handler.wrongReverts(), 0, handler.lastFinding());
    }

    function invariant_AuthorizedCallsSucceed() public view {
        assertEq(handler.authorizedFailures(), 0, handler.lastFinding());
    }

    /// @dev Guards against a vacuous campaign.
    function afterInvariant() public view {
        if (handler.calls() < MIN_CALLS_FOR_COVERAGE_GUARD) return;
        assertGt(handler.authorizedCompletions(), 0, "run without authorized completions");
        assertGt(handler.attacks() + handler.replays(), 0, "run without forged attempts");
    }
}
