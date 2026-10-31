"""Testy podpisów EIP-712 na tymczasowym wektorze z Solidity (T008).

Wartości `PROVISIONAL_*` pochodzą z `contracts/test/SignatureVector.t.sol`
(generowane niezależnie przez `cast wallet sign --data`). Jeśli Python ich nie
odtworzy, to znaczy, że kodowanie EIP-712 rozjeżdża się z kontraktem.
"""

from __future__ import annotations

import pytest
from supply_chain_simulator.canonical import keccak256_hex
from supply_chain_simulator.models import (
    Eip712Domain,
    StageApprovalMessage,
    StageCompletionMessage,
)
from supply_chain_simulator.signing import (
    digest,
    eip712_types,
    recover_signer,
    sign_stage_approval,
    sign_stage_completion,
)
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_1,
    ANVIL_ADDRESS_2,
    ANVIL_KEY_1,
    ANVIL_KEY_2,
    VECTOR_CONTRACT,
    VECTOR_DEADLINE,
    VECTOR_STAGE_ID,
    batch_id,
    vector_domain,
)

BATCH_ID = batch_id("B-2026-0001")
REPORT_HASH = keccak256_hex(b"provisional-report")

PROVISIONAL_COMPLETION_DIGEST = (
    "0xf66529bc5ef30dc02cab89a91a2f77a091b1ef372f72cc42975d81c429462b24"
)
PROVISIONAL_COMPLETION_SIGNATURE = (
    "0x851302742ffcd6b79c30c3926ba8992f8287443c7cc6cc1d32bcb49d3f1b5036"
    "4e349523372ef01e823edc8b2b8e636c9daf1eab41eef30f4d80de7f4bf73e6c1b"
)
PROVISIONAL_APPROVAL_DIGEST = (
    "0xc32c5fb3c18131172585905622d37abd7784569fd01e450b32d9bd34676184e0"
)
PROVISIONAL_APPROVAL_SIGNATURE = (
    "0x8b46aba00f75c04a937337d71e197fb366c379d93a4534678aef167772adc645"
    "2f29fc658b535da13794f290a028b50da691b97d45ddfee3a91641d71dfcb2471b"
)


def completion_message(**overrides: object) -> StageCompletionMessage:
    payload: dict[str, object] = {
        "batchId": BATCH_ID,
        "stageId": VECTOR_STAGE_ID,
        "reportHash": REPORT_HASH,
        "qaPassed": True,
        "deadline": VECTOR_DEADLINE,
    }
    payload.update(overrides)
    return StageCompletionMessage(**payload)


def approval_message(**overrides: object) -> StageApprovalMessage:
    payload: dict[str, object] = {
        "batchId": BATCH_ID,
        "stageId": VECTOR_STAGE_ID,
        "reportHash": REPORT_HASH,
        "deadline": VECTOR_DEADLINE,
    }
    payload.update(overrides)
    return StageApprovalMessage(**payload)


def test_types_contain_domain_and_primary_type() -> None:
    types = eip712_types("StageCompletion")
    assert list(types) == ["EIP712Domain", "StageCompletion"]
    assert [f["type"] for f in types["StageCompletion"]] == [
        "bytes32",
        "uint32",
        "bytes32",
        "bool",
        "uint256",
    ]


def test_unknown_type_is_rejected() -> None:
    with pytest.raises(ValueError):
        eip712_types("StageRejected")


def test_completion_matches_solidity_vector() -> None:
    domain = vector_domain()
    message = completion_message()
    assert digest("StageCompletion", domain, message) == PROVISIONAL_COMPLETION_DIGEST

    signed = sign_stage_completion(domain, message, ANVIL_KEY_1)
    assert signed.digest == PROVISIONAL_COMPLETION_DIGEST
    assert signed.signature == PROVISIONAL_COMPLETION_SIGNATURE
    assert signed.signer.lower() == ANVIL_ADDRESS_1.lower()
    assert recover_signer("StageCompletion", domain, message, signed.signature) == signed.signer


def test_approval_matches_solidity_vector() -> None:
    domain = vector_domain()
    message = approval_message()
    assert digest("StageApproval", domain, message) == PROVISIONAL_APPROVAL_DIGEST

    signed = sign_stage_approval(domain, message, ANVIL_KEY_2)
    assert signed.digest == PROVISIONAL_APPROVAL_DIGEST
    assert signed.signature == PROVISIONAL_APPROVAL_SIGNATURE
    assert signed.signer.lower() == ANVIL_ADDRESS_2.lower()
    assert recover_signer("StageApproval", domain, message, signed.signature) == signed.signer


def test_station_and_operator_are_different_accounts() -> None:
    assert ANVIL_ADDRESS_1 != ANVIL_ADDRESS_2
    assert ANVIL_KEY_1 != ANVIL_KEY_2


def test_completion_and_approval_digests_differ() -> None:
    domain = vector_domain()
    assert digest("StageCompletion", domain, completion_message()) != digest(
        "StageApproval", domain, approval_message()
    )


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("batchId", batch_id("B-2026-0002")),
        ("stageId", 3),
        ("reportHash", keccak256_hex(b"provisional-report-2")),
        ("deadline", VECTOR_DEADLINE + 1),
    ],
)
def test_every_signed_field_changes_both_digests(field: str, value: object) -> None:
    domain = vector_domain()
    baseline_completion = digest("StageCompletion", domain, completion_message())
    baseline_approval = digest("StageApproval", domain, approval_message())

    assert digest("StageCompletion", domain, completion_message(**{field: value})) != (
        baseline_completion
    )
    assert digest("StageApproval", domain, approval_message(**{field: value})) != (
        baseline_approval
    )


def test_qa_passed_is_part_of_the_completion_signature_only() -> None:
    domain = vector_domain()
    assert digest("StageCompletion", domain, completion_message(qaPassed=False)) != digest(
        "StageCompletion", domain, completion_message()
    )


@pytest.mark.parametrize(
    "domain",
    [
        Eip712Domain(name="SupplyChain", version="1", chainId=80002, verifyingContract=VECTOR_CONTRACT),
        Eip712Domain(name="SupplyChain", version="2", chainId=31337, verifyingContract=VECTOR_CONTRACT),
        Eip712Domain(
            name="SupplyChain",
            version="1",
            chainId=31337,
            verifyingContract="0x0000000000000000000000000000000000000001",
        ),
    ],
)
def test_domain_change_invalidates_the_signature(domain: Eip712Domain) -> None:
    assert digest("StageCompletion", domain, completion_message()) != PROVISIONAL_COMPLETION_DIGEST


def test_signature_does_not_verify_against_another_digest() -> None:
    domain = vector_domain()
    signed = sign_stage_completion(domain, completion_message(), ANVIL_KEY_1)
    other = completion_message(stageId=1)
    assert recover_signer("StageCompletion", domain, other, signed.signature).lower() != (
        ANVIL_ADDRESS_1.lower()
    )
