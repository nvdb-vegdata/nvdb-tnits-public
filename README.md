# TN-ITS-eksport fra NVDB

## Utvikleroppsett

- Kjør `./gradlew installGitHooks` for å installere pre-commit hook som kjører formattering før commit.
- Kjør `docker compose up -d` for å starte MinIO.
- Kjør `./gradlew tnits-generator:run` for å starte generatoren. Den vil automatisk utføre backfill og generere snapshot.
- Kjør `./gradlew tnits-katalog:bootRun` for å starte en enkel katalogtjeneste som serverer filer fra MinIO.

## Fartsgrenser

### Valg og antagelser

Vi setter følgende TN-ITS-felt der det kan være tvetydighet:

- `validFrom`: Settes til startdato for vegobjektets første versjon
- `beginLifespanVersion`: Settes til startdato for vegobjektets gjeldende versjon

For OpenLR gjør vi følgende valg:

- `frc`: Leses fra vegobjekt 821 Funksjonell Vegklasse, med tilbakefall til FRC 7. Hvis det finnes flere vegklasser på samme veglenke, velges den med lavest viktighet (høyest FRC-verdi).
- tillatt kjøreretning: Leses fra veglenkens feltoversikt, eller fra vegobjekt 616 Feltstrekning for konnekteringslenker.

### Lukking og fjerning

- Ved lukking av et vegobjekt, setter vi UpdateType til `Modify`, og `validTo` til sluttdato for vegobjektets siste versjon.
- Ved fjerning/sletting av et vegobjekt, viser vi slik vegobjektet så ut før det ble slettet, og vi setter UpdateType til `Remove`, og `validTo` til dato for selve eksporten.

#### API-modeller vs domenemodeller

Vi bruker domenemodeller for serialisert lagring og logikk, ellers benyttes API-modeller direkte, for å redusere boilerplate og mapping-kode.
