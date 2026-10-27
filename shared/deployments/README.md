# Publiczne dane wdrożeń

**Język:** JSON.

Planowane pliki `local.example.json` i `amoy.json`: chainId, adres kontraktu,
deployment block, hash transakcji, wersja kontraktu/kompilatora i publiczne
parametry domeny EIP-712. Nie dopisuj pliku Amoy przed faktycznym wdrożeniem.

Lokalny adres po restarcie sieci może się zmienić; przykład nie jest aktywnym
wdrożeniem. Rzeczywiste lokalne dane dostarczaj konfiguracją środowiska
nie myląc ich z trwałym adresem Amoy.

Aktualizuj po wykonaniu i sprawdzeniu deploymentu. Backend i frontend muszą
używać tej samej sieci oraz adresu. Nigdy nie dodawaj prywatnych kluczy,
URL RPC z tokenem ani sekretów administratora.
