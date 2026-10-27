# Przykłady i wektory testowe

**Język:** JSON; kanoniczny raport jako bajty UTF-8 zapisane w pliku tekstowym.

Plan: `report-valid.json`, `report-qa-fail.json`, `signature-vector.json`
(`StageCompletion` bez nonce, `StageApproval`; wektor wspólny dla Python, Java, TS/viem i Solidity)
oraz przykładowe odpowiedzi partii/historii/polecenia zgodne z OpenAPI.
Wektor zawiera kanoniczny raport, oczekiwany reportHash, typed data, podpis
i publiczny adres testowej stacji. Wartości kryptograficzne trzeba wygenerować
i zweryfikować, nie wpisać jako losowe przykłady.

Klucz testowy odtwarza kod testów lokalnie z jasno oznaczonego, nieprodukcyjnego
mechanizmu; nie dodawaj rzeczywistych kluczy do JSON. Fixture'y nie zawierają
tokenów, rzeczywistych danych produkcyjnych ani kont z finansowaniem.

Każdy moduł czyta te same dane. Testy negatywne mogą mutować poprawny fixture,
żeby udowodnić odrzucenie. Po zmianie schematu aktualizuj wszystkie przykłady.
Zapisz, które dane są raportem, a które obwiednią podpisu - nie hashuj obwiedni.

Dodaj przykład certyfikatu stacji z testowym wystawcą oraz wektor
współzatwierdzenia kontroli: podpis stacji i podpis `StageApproval` niezależnego operatora zmiany
dla tej samej partii/etapu/hash; numer partii `B-RRRR-NNNN` i `batchId = keccak256(utf8(batchNumber))`. Testuj też ten sam adres po obu stronach
i brakujący/odwołany certyfikat oraz niezgodność z rejestrem stacji. Nie wymyślaj poprawnych hashy i podpisów ręcznie.
