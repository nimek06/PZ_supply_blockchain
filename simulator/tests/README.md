# Testy symulatora

**Język:** Python, pytest.

Plan: `test_models.py`, `test_signing.py`, `test_client.py`, `test_runner.py`.
Użyj fixture'ów testowych dla zegara, seeda i testowego signera.
Mock/stub klienta HTTP ma sprawdzać body, status, timeout i liczbę żądań.

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
