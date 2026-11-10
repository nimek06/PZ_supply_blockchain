# Scenariusze linii

**Język:** YAML, walidowany modelem Pydantic (`scenario.py`).

Plan: `ebike-happy-path.yaml`, `ebike-qa-fail.yaml`, docelowo `out-of-order`,
`retry-after-timeout`. Opisz stacje, seed, kolejność kroków, parametry/anomalie oraz
oczekiwane wyniki. Nazwy etapów mapuj do uzgodnionej listy (0..3). Nie dodawaj
executable Python ani `eval` do danych scenariusza.

Scenariusze nie zawierają kluczy. Adres/sekret stacji pochodzą z konfiguracji
(`config.py`; CLI `--station-key`/`--certificate`). Timestamp, `deadline` i podpis
tworzy runner (brak nonce); zapisany stary podpis szybko wygasa. Test negatywny ma
jawnie określać spodziewany błąd i brak przejścia stanu; `ebike-qa-fail.yaml`
(etap 1, `qaPassed=false` zgodne z profilem) oczekuje `outcome=REJECTED`
(`StageRejected`, `202`), nie `422`. Scenariusz retry powtarza tę samą treść,
nie dwa losowe raporty.

Dodaj scenariusz kontroli z podpisem stacji i osobnym zatwierdzeniem operatora
zmiany (`StageApproval`) oraz negatywne warianty bez drugiego klucza i z tym samym adresem.
Osobne przypadki: odwołany certyfikat stacji, stacja wyrejestrowana/poza `stageMask`.
Podpisu operatora nie generuj kluczem stacji ukrytym pod inną nazwą.

Uruchomienie: `python -m supply_chain_simulator --scenario scenarios/ebike-happy-path.yaml
--backend-url ... --rpc-url ... --verifying-contract ... --station-key ... --certificate ...`.
