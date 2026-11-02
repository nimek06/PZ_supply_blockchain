# Tydzień 1 - Kubo (IPFS)

Sprawdzane 2026-11-02 na `ipfs/kubo:v0.32.1` z naszego Compose.

- wysyłałem bajty JCS raportu z wektora jako plik przez `POST /api/v0/add`
- wektor był potem przegenerowany pod e-bike, więc CID niżej są ze starej wersji, ale wnioski się nie zmieniają
- domyślnie Kubo daje CIDv0: `QmYc61wGUCUtLBJYN97wBHsAFZN1bkri1DsPnDeymHuDM9`
- z `cid-version=1` daje CIDv1 (raw): `bafkreic6hfd5nlfch6sjutxn63tbjptj4x4pxo5eeocylmhb5gko2q4ngy`
- te same bajty wysłane drugi raz dają ten sam CID
- plik pobrany przez bramkę (`http://127.0.0.1:8081/ipfs/<cid>`) ma identyczne bajty, a jego Keccak to dokładnie `reportHash`
- ten sam raport z wcięciami daje inny CID, więc trzeba wysyłać dokładnie bajty JCS
- nieznany CID na węźle `--offline` od razu zwraca 404
- do adaptera w tygodniu 4: walidator ma przyjmować CIDv0 i CIDv1, CID to nie `reportHash`, a ponowienie po timeoucie niczego nie dubluje
- ja bym wysyłał z `cid-version=1`, bo wtedy CID zależy tylko od bajtów
- Pinaty nie sprawdzałem, token ma Mikołaj - trzeba zobaczyć, jaki CID zwraca, czy hash po pobraniu się zgadza i jakie są limity darmowego planu

Jak powtórzyć:

```bash
cd infrastructure/local && docker compose up -d
python3 -c "import json;open('/tmp/report.jcs','wb').write(json.load(open('../../shared/fixtures/signature-vector.json'))['canonicalReportJcs'].encode())"
curl -s -X POST -F "file=@/tmp/report.jcs" "http://127.0.0.1:5001/api/v0/add?cid-version=1"
curl -s http://127.0.0.1:8081/ipfs/<cid> | cmp - /tmp/report.jcs && echo identyczne
docker exec -i pz-supply-anvil-1 cast keccak "$(cat /tmp/report.jcs)"
```
