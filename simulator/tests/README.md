# Testy symulatora

**Język:** Python, pytest.

Plan: `test_models.py`, `test_signing.py`, `test_client.py`, `test_runner.py`.
Użyj fixture'ów testowych dla zegara, seeda i testowego signera.
Mock/stub klienta HTTP ma sprawdzać body, status, timeout i liczbę żądań.

**Stan 2026-11-23:** gotowe `test_cli.py`, `test_models.py`, `test_canonical.py`,
`test_signing.py`, `test_signature_vector.py`, `test_negative_fixtures.py`,
`test_certificate.py`, `test_certificate_fixture.py`, `test_certificate_negatives.py`,
`test_identity.py`, `test_profiles.py`, `test_telemetry.py`, `test_scenario.py`,
`test_config.py`, `test_chain.py`, `test_runner.py`, `test_cli_scenario.py` i `test_client.py`
(klient na transporcie `httpx.MockTransport` - bez sieci i bez prawdziwego snu).
Pola odpowiedzi (`type`, `status`, `statusUrl`, `failureCode`) są sprawdzane wobec
`shared/schemas/openapi.yaml`.

Kanoniczne uruchomienie lokalne (stos + kontrakty + backend + stacja + scenariusz)
opisuje `backend/scripts/running-local-e2e.md`; te testy pozostają jednostkowe
(bez Dockera i bez sieci).

Sprawdź strict validation, jednostki, kanoniczny hash oraz wspólny wektor
z shared/fixtures. Zmiana podpisanej treści musi być wykrywalna.
Retry ma zachować podpis i raport (brak nonce); błędny podpis/QA nie ma nieskończonego retry.
Runner nie przechodzi do następnego etapu po samym `202`.
Sprawdź zgodność certyfikatu z signerem i rejestrem stacji, wygaśnięcie, odwołanie i nieznanego
wystawcę. W integracji kontroli drugi klucz należy do operatora zmiany;
klucz relayera ani ponowny podpis stacji nie zastępują tego dowodu.

Osoba 4 koordynuje także testy całego systemu opisane w
[testing](../../docs/testing.md), nie tylko testy Python.

Testy nie wymagają sieci ani prawdziwych tokenów. Osobny test integracyjny
może używać jawnie skonfigurowanego lokalnego API/Anvil.
Po instalacji w venv: `python -m pytest`.
