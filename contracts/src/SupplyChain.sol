// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";

/// @title SupplyChain
/// @notice On-chain guard of the production route 0 -> 1 -> 2 -> 3 (protocol v1, changes v2).
/// Stations sign `StageCompletion` off-chain, the relayer submits it; the QA stage additionally
/// requires a `StageApproval` signed by an independent shift operator holding QA_INSPECTOR_ROLE.
contract SupplyChain is AccessControl, EIP712 {
    bytes32 public constant RELAYER_ROLE = keccak256("RELAYER_ROLE");
    bytes32 public constant QA_INSPECTOR_ROLE = keccak256("QA_INSPECTOR_ROLE");
    /// @dev Checked only by the backend API; the contract does not use it.
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    bytes32 public constant STAGE_COMPLETION_TYPEHASH = keccak256(
        "StageCompletion(bytes32 batchId,uint32 stageId,bytes32 reportHash,bool qaPassed,uint256 deadline)"
    );
    bytes32 public constant STAGE_APPROVAL_TYPEHASH = keccak256(
        "StageApproval(bytes32 batchId,uint32 stageId,bytes32 reportHash,uint256 deadline)"
    );

    /// @notice Stages: 0 preparation, 1 assembly, 2 quality control, 3 packaging.
    uint32 public constant STAGE_COUNT = 4;
    uint32 public constant QA_STAGE = 2;
    /// @dev Bit mask of all valid stages (bit i = stage i).
    uint32 public constant ALL_STAGES_MASK = 0x0F;
    uint256 public constant MAX_CID_LENGTH = 128;

    struct Batch {
        bool exists;
        bool completed;
        uint32 nextStage;
    }

    struct Station {
        bool active;
        uint64 expiresAt;
        uint32 stageMask;
        bytes32 stationId;
    }

    mapping(bytes32 batchId => Batch) private _batches;
    mapping(address station => Station) private _stations;
    /// @notice StageCompletion digests already recorded as StageRejected (no duplicate events).
    mapping(bytes32 digest => bool) public rejectionRecorded;
    uint256 public maxDeadlineWindow;

    event BatchCreated(bytes32 indexed batchId, string batchNumber);
    event StageCompleted(
        bytes32 indexed batchId,
        uint32 stageId,
        address indexed station,
        address qaOperator,
        bytes32 reportHash,
        string ipfsCid
    );
    event StageRejected(
        bytes32 indexed batchId,
        uint32 stageId,
        address indexed station,
        bytes32 reportHash,
        string ipfsCid
    );
    event StationRegistered(
        address indexed station, bytes32 stationId, uint64 expiresAt, uint32 stageMask
    );
    event StationRevoked(address indexed station);
    event MaxDeadlineWindowUpdated(uint256 previousWindow, uint256 newWindow);

    error InvalidAddress();
    error InvalidBatchNumber();
    error BatchAlreadyExists(bytes32 batchId);
    error UnknownBatch(bytes32 batchId);
    error BatchAlreadyCompleted(bytes32 batchId);
    error UnexpectedStage(uint32 expected, uint32 actual);
    error DeadlineExpired(uint256 deadline);
    error DeadlineTooFar(uint256 deadline, uint256 maxAllowed);
    error InvalidReportHash();
    error InvalidCid();
    error InvalidStationSignature();
    error StationNotActive(address station);
    error StationExpired(address station, uint64 expiresAt);
    error StageNotAllowed(address station, uint32 stageId);
    error RejectionAlreadyRecorded(bytes32 digest);
    error MissingApproval();
    error ApprovalExpired(uint256 approvalDeadline);
    error InvalidApprovalSignature();
    error OperatorNotQaInspector(address operator);
    error SameStationAndOperator(address account);
    error InvalidStationRegistration();
    error InvalidDeadlineWindow();

    constructor(address admin, uint256 initialMaxDeadlineWindow) EIP712("SupplyChain", "1") {
        if (admin == address(0)) revert InvalidAddress();
        if (initialMaxDeadlineWindow == 0) revert InvalidDeadlineWindow();
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        maxDeadlineWindow = initialMaxDeadlineWindow;
        emit MaxDeadlineWindowUpdated(0, initialMaxDeadlineWindow);
    }

    // ---------------------------------------------------------------- admin

    function registerStation(address station, bytes32 stationId, uint64 expiresAt, uint32 stageMask)
        external
        onlyRole(DEFAULT_ADMIN_ROLE)
    {
        if (station == address(0)) revert InvalidAddress();
        if (
            stationId == bytes32(0) || expiresAt <= block.timestamp || stageMask == 0
                || stageMask & ~ALL_STAGES_MASK != 0
        ) revert InvalidStationRegistration();
        _stations[station] = Station({
            active: true, expiresAt: expiresAt, stageMask: stageMask, stationId: stationId
        });
        emit StationRegistered(station, stationId, expiresAt, stageMask);
    }

    function revokeStation(address station) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (!_stations[station].active) revert StationNotActive(station);
        _stations[station].active = false;
        emit StationRevoked(station);
    }

    function setMaxDeadlineWindow(uint256 seconds_) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (seconds_ == 0) revert InvalidDeadlineWindow();
        emit MaxDeadlineWindowUpdated(maxDeadlineWindow, seconds_);
        maxDeadlineWindow = seconds_;
    }

    // -------------------------------------------------------------- relayer

    /// @notice Creates a batch; `batchId = keccak256(utf8(batchNumber))`, format `B-RRRR-NNNN`.
    function createBatch(string calldata batchNumber)
        external
        onlyRole(RELAYER_ROLE)
        returns (bytes32 batchId)
    {
        if (!_isValidBatchNumber(bytes(batchNumber))) {
            revert InvalidBatchNumber();
        }
        batchId = keccak256(bytes(batchNumber));
        if (_batches[batchId].exists) revert BatchAlreadyExists(batchId);
        _batches[batchId] = Batch({exists: true, completed: false, nextStage: 0});
        emit BatchCreated(batchId, batchNumber);
    }

    /// @notice Completes (or, with `qaPassed == false`, records a rejection of) the next stage.
    /// `approvalDeadline`/`approvalSignature` are only evaluated for the QA stage with a pass.
    function completeStage(
        bytes32 batchId,
        uint32 stageId,
        bytes32 reportHash,
        bool qaPassed,
        uint256 deadline,
        string calldata ipfsCid,
        bytes calldata stationSignature,
        uint256 approvalDeadline,
        bytes calldata approvalSignature
    ) external onlyRole(RELAYER_ROLE) {
        _checkBatchAndStage(batchId, stageId);
        _checkDeadline(deadline);
        _checkReport(reportHash, ipfsCid);
        address station =
            _verifyStation(batchId, stageId, reportHash, qaPassed, deadline, stationSignature);

        if (!qaPassed) {
            _recordRejection(batchId, stageId, reportHash, deadline, station, ipfsCid);
            return;
        }

        address qaOperator = address(0);
        if (stageId == QA_STAGE) {
            qaOperator = _checkApproval(
                batchId, stageId, reportHash, station, approvalDeadline, approvalSignature
            );
        }
        _advance(batchId, stageId, station, qaOperator, reportHash, ipfsCid);
    }

    // ---------------------------------------------------------------- views

    function getBatch(bytes32 batchId)
        external
        view
        returns (bool exists, uint32 nextStage, bool completed)
    {
        Batch storage batch = _batches[batchId];
        return (batch.exists, batch.nextStage, batch.completed);
    }

    function getStation(address station)
        external
        view
        returns (bool active, bytes32 stationId, uint64 expiresAt, uint32 stageMask)
    {
        Station storage s = _stations[station];
        return (s.active, s.stationId, s.expiresAt, s.stageMask);
    }

    function domainSeparator() external view returns (bytes32) {
        return _domainSeparatorV4();
    }

    /// @notice EIP-712 digest of `StageCompletion` signed by the station.
    function hashStageCompletion(
        bytes32 batchId,
        uint32 stageId,
        bytes32 reportHash,
        bool qaPassed,
        uint256 deadline
    ) public view returns (bytes32) {
        return _hashTypedDataV4(
            keccak256(
                abi.encode(
                    STAGE_COMPLETION_TYPEHASH, batchId, stageId, reportHash, qaPassed, deadline
                )
            )
        );
    }

    /// @notice EIP-712 digest of `StageApproval` signed by the QA operator.
    function hashStageApproval(
        bytes32 batchId,
        uint32 stageId,
        bytes32 reportHash,
        uint256 deadline
    ) public view returns (bytes32) {
        return _hashTypedDataV4(
            keccak256(abi.encode(STAGE_APPROVAL_TYPEHASH, batchId, stageId, reportHash, deadline))
        );
    }

    // ------------------------------------------------------------- internal

    function _checkBatchAndStage(bytes32 batchId, uint32 stageId) private view {
        Batch storage batch = _batches[batchId];
        if (!batch.exists) revert UnknownBatch(batchId);
        if (batch.completed) revert BatchAlreadyCompleted(batchId);
        if (stageId != batch.nextStage) revert UnexpectedStage(batch.nextStage, stageId);
    }

    function _checkDeadline(uint256 deadline) private view {
        if (block.timestamp > deadline) revert DeadlineExpired(deadline);
        uint256 maxAllowed = block.timestamp + maxDeadlineWindow;
        if (deadline > maxAllowed) revert DeadlineTooFar(deadline, maxAllowed);
    }

    function _checkReport(bytes32 reportHash, string calldata ipfsCid) private pure {
        if (reportHash == bytes32(0)) revert InvalidReportHash();
        uint256 cidLength = bytes(ipfsCid).length;
        if (cidLength == 0 || cidLength > MAX_CID_LENGTH) revert InvalidCid();
    }

    function _verifyStation(
        bytes32 batchId,
        uint32 stageId,
        bytes32 reportHash,
        bool qaPassed,
        uint256 deadline,
        bytes calldata stationSignature
    ) private view returns (address station) {
        station = _recover(
            hashStageCompletion(batchId, stageId, reportHash, qaPassed, deadline), stationSignature
        );
        if (station == address(0)) revert InvalidStationSignature();
        _checkStation(station, stageId);
    }

    /// @dev Only reached with qaPassed == false; the digest is recomputed to keep the stack small.
    function _recordRejection(
        bytes32 batchId,
        uint32 stageId,
        bytes32 reportHash,
        uint256 deadline,
        address station,
        string calldata ipfsCid
    ) private {
        bytes32 digest = hashStageCompletion(batchId, stageId, reportHash, false, deadline);
        if (rejectionRecorded[digest]) revert RejectionAlreadyRecorded(digest);
        rejectionRecorded[digest] = true;
        emit StageRejected(batchId, stageId, station, reportHash, ipfsCid);
    }

    function _advance(
        bytes32 batchId,
        uint32 stageId,
        address station,
        address qaOperator,
        bytes32 reportHash,
        string calldata ipfsCid
    ) private {
        Batch storage batch = _batches[batchId];
        uint32 next = stageId + 1;
        batch.nextStage = next;
        if (next == STAGE_COUNT) batch.completed = true;
        emit StageCompleted(batchId, stageId, station, qaOperator, reportHash, ipfsCid);
    }

    function _checkStation(address station, uint32 stageId) private view {
        Station storage s = _stations[station];
        if (!s.active) revert StationNotActive(station);
        if (block.timestamp > s.expiresAt) revert StationExpired(station, s.expiresAt);
        if (s.stageMask & (uint32(1) << stageId) == 0) revert StageNotAllowed(station, stageId);
    }

    function _checkApproval(
        bytes32 batchId,
        uint32 stageId,
        bytes32 reportHash,
        address station,
        uint256 approvalDeadline,
        bytes calldata approvalSignature
    ) private view returns (address operator) {
        if (approvalSignature.length == 0) {
            revert MissingApproval();
        }
        if (block.timestamp > approvalDeadline) revert ApprovalExpired(approvalDeadline);
        operator = _recover(
            hashStageApproval(batchId, stageId, reportHash, approvalDeadline), approvalSignature
        );
        if (operator == address(0)) revert InvalidApprovalSignature();
        if (!hasRole(QA_INSPECTOR_ROLE, operator)) revert OperatorNotQaInspector(operator);
        if (operator == station) revert SameStationAndOperator(operator);
    }

    /// @dev Malleability-safe recovery; returns address(0) for any malformed signature.
    function _recover(bytes32 digest, bytes calldata signature) private pure returns (address) {
        (address signer, ECDSA.RecoverError err,) = ECDSA.tryRecover(digest, signature);
        return err == ECDSA.RecoverError.NoError ? signer : address(0);
    }

    /// @dev Accepts exactly `B-` + 4 digits + `-` + 4 digits (DEC-12).
    function _isValidBatchNumber(bytes calldata s) private pure returns (bool) {
        if (s.length != 11 || s[0] != "B" || s[1] != "-" || s[6] != "-") return false;
        for (uint256 i = 2; i < 11; ++i) {
            if (i == 6) continue;
            if (s[i] < "0" || s[i] > "9") return false;
        }
        return true;
    }
}
