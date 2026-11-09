// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {SupplyChain} from "../src/SupplyChain.sol";
import {SupplyChainBase} from "./SupplyChain.t.sol";

/// @notice Scenariusz raportu gazu:
/// `forge test --match-path test/SupplyChain.gas.t.sol --gas-report`.
/// Kontrakt jest wdrażany przez `new`, bo Forge rozpoznaje w raporcie tylko kontrakty utworzone
/// przez CREATE (`deployCodeTo` z `SupplyChainBase` używa `vm.etch`). Przy `isolate = true`
/// (domyślne w Forge 1.8) każde wywołanie zmieniające stan jest osobną transakcją, więc liczby
/// zawierają koszt bazowy 21 000 i calldata; funkcje `view` pokazują sam koszt wykonania.
contract SupplyChainGasTest is SupplyChainBase {
    function setUp() public override {
        vm.warp(1_800_000_000);
        (station, stationKey) = makeAddrAndKey("station");
        (operator, operatorKey) = makeAddrAndKey("operator");

        sc = new SupplyChain(admin, WINDOW);

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

    function test_Gas_FullRoute() public {
        for (uint32 stageId = 0; stageId < 4; ++stageId) {
            _completeValid(stageId);
        }
        _assertBatch(4, true);
    }

    function test_Gas_Rejection() public {
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        _submit(0, h, false, deadline, _signCompletion(stationKey, 0, h, false, deadline), 0, "");
        _assertBatch(0, false);
    }

    /// @dev Najdroższy wariant `completeStage`: odrzucenie z CID o maksymalnej długości
    /// (górna granica gazu dla relayera).
    function test_Gas_RejectionMaxCid() public {
        bytes memory cid = new bytes(sc.MAX_CID_LENGTH());
        for (uint256 i = 0; i < cid.length; ++i) {
            cid[i] = "b";
        }
        uint256 deadline = _deadline();
        bytes32 h = _report(0);
        bytes memory sig = _signCompletion(stationKey, 0, h, false, deadline);
        vm.prank(relayer);
        sc.completeStage(batchId, 0, h, false, deadline, string(cid), sig, 0, "");
        _assertBatch(0, false);
    }

    function test_Gas_AdminOperations() public {
        (address second,) = makeAddrAndKey("second-station");
        vm.startPrank(admin);
        sc.registerStation(
            second, keccak256("ST-002"), uint64(block.timestamp + 365 days), ALL_STAGES
        );
        sc.revokeStation(second);
        sc.revokeRole(sc.QA_INSPECTOR_ROLE(), operator);
        sc.setMaxDeadlineWindow(2 * WINDOW);
        vm.stopPrank();

        (bool active,,,) = sc.getStation(second);
        assertFalse(active);
        assertFalse(sc.hasRole(sc.QA_INSPECTOR_ROLE(), operator));
    }
}
