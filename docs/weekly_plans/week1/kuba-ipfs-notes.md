# Tydzień 1 - notatka Kuby: magazyn raportów (Kubo, Pinata)

**Data:** 2026-11-02. **Kubo:** `ipfs/kubo:v0.32.1` z `infrastructure/local/compose.yaml` (tryb `--offline`).
**Dane:** dokładne bajty JCS raportu z `signature-vector.json` (296 bajtów),
`reportHash` = `0x4707421ac9d17015745b8d29d2684f271eb6d37cc2a22f7f01456b88a894e97d`.

## Kubo - wyniki

Upload jako plik (`multipart/form-data`, pole `file`) przez `POST /api/v0/add`:

| Wywołanie | Zwrócony CID | Uwagi |
|---|---|---|
| `add?pin=true` (domyślne) | `QmYc61wGUCUtLBJYN97wBHsAFZN1bkri1DsPnDeymHuDM9` | **CIDv0**, dag-pb (UnixFS), `Size` 307 = bajty + opakowanie UnixFS |
| `add?pin=true&cid-version=1` | `bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngy` | **CIDv1**, kodek `raw`, sha2-256, base32; `Size` 296 = dokładnie bajty |
| to samo drugi raz | ten sam CIDv1 | upload jest idempotentny: te same bajty dają ten sam CID |

Pobranie przez bramkę `http://127.0.0.1:8081/ipfs/<cid>`: dla obu CID **HTTP 200**,
`Content-Type: application/json`, a pobrane bajty są **identyczne bajt w bajt** z wysłanymi.
Keccak-256 pobranych bajtów (`cast keccak`) = `reportHash` z wektora dla obu CID.

Kontrola: ten sam raport sformatowany z wcięciami daje inny CID (`bafkreidib6rf...`) i inny hash.
To potwierdza regułę z protokołu: wysyłamy bajty JCS, nigdy obiekt JSON do ponownej serializacji.

Nieznany CID na węźle `--offline`: bramka od razu zwraca **HTTP 404** (nie wisi). Węzeł z siecią
szukałby w DHT, więc adapter i tak potrzebuje read timeout.

## Wnioski dla adaptera (tydzień 4)

- Domyślny `add` daje CIDv0, a `cid-version=1` daje CIDv1 `raw`. Walidator musi przyjmować oba
  (DEC-14). **Propozycja:** `KuboReportStorage` wysyła `cid-version=1`, bo CID zależy wtedy tylko
  od bajtów i hashu, a nie od opakowania UnixFS. Do potwierdzenia, jaki CID zwraca Pinata.
- CID to nie `reportHash` (sha2-256 vs Keccak-256) - nie porównywać ich jako tekstu.
- Ponowienie po timeoucie jest bezpieczne: ten sam plik = ten sam CID, nie powstaje duplikat.
- API Kubo (5001) nie ma uwierzytelnienia - zostaje na `127.0.0.1`.

## Pinata - nie zbadano

Konto i token ma Mikołaj; tokenu nie wpisujemy do repozytorium ani do czatu. Do sprawdzenia:
wersja zwracanego CID dla uploadu pliku (aktualne API uploadu, nie legacy `pinJSONToIPFS`),
pobranie przez bramkę Pinaty i hash po pobraniu oraz limity darmowego planu (liczba plików,
requesty na minutę i na miesiąc), bo pętla symulatora może je szybko zużyć.

## Jak powtórzyć

```bash
cd infrastructure/local && docker compose up -d
python3 -c "import json;open('/tmp/report.jcs','wb').write(json.load(open('../../shared/fixtures/signature-vector.json'))['canonicalReportJcs'].encode())"
curl -s -X POST -F "file=@/tmp/report.jcs" "http://127.0.0.1:5001/api/v0/add?cid-version=1"
curl -s http://127.0.0.1:8081/ipfs/<cid> | cmp - /tmp/report.jcs && echo identyczne
docker exec -i pz-supply-anvil-1 cast keccak "$(cat /tmp/report.jcs)"
```

(`shared/fixtures/signature-vector.json` jest na gałęzi Roberta; do czasu merge'a ten sam plik leży
w `backend/src/test/resources/fixtures/`.)
