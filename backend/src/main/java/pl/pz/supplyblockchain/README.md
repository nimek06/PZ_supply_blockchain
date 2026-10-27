# Pakiet główny backendu

**Język:** Java 21. Bazowy pakiet: `pl.pz.supplyblockchain`.

Tutaj utwórz `SupplyBlockchainApplication.java` z `@SpringBootApplication`,
aby skanowanie objęło wszystkie podpakiety.

- [api](api/README.md): kontrolery, HTTP i wyjątki transportowe.
- [application](application/README.md): przypadki użycia i porty.
- [domain](domain/README.md): model i reguły bez bibliotek infrastruktury.
- [infrastructure](infrastructure/README.md): implementacje integracji.
- [config](config/README.md): konfiguracja i wiązanie beanów.

Nie dopisuj logiki biznesowej do metody `main`. Nie twórz równoległej
struktury `service/repository/model` poza tym podziałem.
