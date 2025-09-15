# TN-ITS-eksport fra NVDB

## Fartsgrenser

### Valg og antagelser

Vi setter følgende TN-ITS-felt der det kan være tvetydighet:

- `validFrom`: Settes til startdato for vegobjektets første versjon
- `beginLifespanVersion`: Settes til startdato for vegobjektets gjeldende versjon

For OpenLR gjør vi følgende valg:

- `frc`: Leses fra vegobjekt 821 Funksjonell Vegklasse, med tilbakefall til FRC 7. Hvis det finnes flere vegklasser på samme veglenke, velges den med lavest viktighet (høyest FRC-verdi).
- tillatt kjøreretning: Leses fra veglenkens feltoversikt, eller fra vegobjekt 616 Feltstrekning for konnekteringslenker.

