# TN-ITS-eksport fra NVDB

## Dokumentasjon

Dokumentasjon på engelsk finnes i [docs/](docs/):

- [GETTING_STARTED.md](docs/GETTING_STARTED.md) - Komme i gang
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) - Arkitektur
- [CONCEPTS.md](docs/CONCEPTS.md) - Viktige konsepter
- [DATA_FLOW.md](docs/DATA_FLOW.md) - Dataflyt
- [STORAGE.md](docs/STORAGE.md) - Lagring
- [TESTING.md](docs/TESTING.md) - Testing
- [TNITS_EXPORT.md](docs/TNITS_EXPORT.md) - Om TN-ITS eksport

## Utvikleroppsett

- Kjør `./gradlew installGitHooks` for å installere pre-commit hook som kjører formattering før commit.
- Kjør `docker compose up -d` for å starte MinIO.
- Kjør `./gradlew tnits-generator:run` for å starte generatoren. Den vil automatisk utføre backfill og generere snapshot.
- Kjør `./gradlew tnits-katalog:bootRun` for å starte en enkel katalogtjeneste som serverer filer fra MinIO.

## Fartsgrenser

### Valg og antagelser

Vi setter følgende TN-ITS-felt der det kan være tvetydighet:

- `validFrom`: Settes til startdato for vegobjektets første versjon
- `validTo`, `endLivespanVersion`: Settes til sluttdato for vegobjektets siste versjon ved lukking. For slettede vegobjekter settes den til dato for selve eksporten.
- `beginLifespanVersion`: Settes til startdato for vegobjektets gjeldende versjon

For OpenLR gjør vi følgende valg:

- `frc`: Leses fra vegobjekt 821 Funksjonell Vegklasse, med tilbakefall til FRC 7. Hvis det finnes flere vegklasser på samme veglenke, velges den med lavest viktighet (høyest FRC-verdi).
- tillatt kjøreretning: Leses fra veglenkens feltoversikt, eller fra vegobjekt 616 Feltstrekning for konnekteringslenker.

### Lukking og fjerning

- Ved lukking av et vegobjekt, setter vi UpdateType til `Modify`, og `validTo` til sluttdato for vegobjektets siste versjon.
- Ved fjerning/sletting av et vegobjekt, viser vi slik vegobjektet så ut før det ble slettet, og vi setter UpdateType til `Remove`, og `validTo` til dato for selve eksporten.

#### API-modeller vs domenemodeller

Vi bruker domenemodeller for serialisert lagring og logikk, ellers benyttes API-modeller direkte, for å redusere boilerplate og mapping-kode.
