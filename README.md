# XML Export from NVDB

This project enables generation and publication of XML exports of road data from NVDB (Norwegian Road Database), in accordance with [TN-ITS](https://tn-its.eu/standardisation/) and [INSPIRE](https://inspire.ec.europa.eu/).

## Public repository and reference

This is a public repository that also serves as a good example of how to integrate with roadnet data from NVDB using backfill + event-based updates.

## Documentation

Detailed documentation can be found in [docs](docs):

- [GETTING_STARTED.md](docs/GETTING_STARTED.md) - Getting Started
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) - Architecture
- [CONCEPTS.md](docs/CONCEPTS.md) - Important Concepts
- [DATA_FLOW.md](docs/DATA_FLOW.md) - Data Flow
- [STORAGE.md](docs/STORAGE.md) - Storage
- [TESTING.md](docs/TESTING.md) - Testing
- [TNITS_EXPORT.md](docs/TNITS_EXPORT.md) - About TN-ITS Export
- [INSPIRE_ROADNET_EXPORT.md](docs/INSPIRE_ROADNET_EXPORT.md) - About INSPIRE Export

## Developer Setup

- Run `./gradlew installGitHooks` to install pre-commit hook that runs formatting before commits.
- Run `docker compose up -d` to start MinIO.
- Run `./gradlew tnits-generator:run` to start the generator. It will automatically perform backfill and generate a snapshot.
- Run `./gradlew tnits-katalog:bootRun` to start a simple catalog service that serves files from MinIO.

## TN-ITS Export Viewer

The `viewer.html` file provides a web-based viewer for exploring the exported TN-ITS data. When changes to `viewer.html` are pushed to the main branch on GitHub, a GitHub workflow automatically deploys it to GitHub Pages as `index.html`.

View the live viewer at: https://nvdb-vegdata.github.io/nvdb-tnits-public/

**Note:** The viewer must be pushed to GitHub (not the default SVV Bitbucket) to trigger the deployment workflow.

Add GitHub as a remote:

```bash
git remote add github git@github.com:nvdb-vegdata/nvdb-tnits-public.git
```

Push the main branch to GitHub:

```bash
git push github main
```

## SVV Atlas configuration

Deployment details for SVV Atlas hosting are found in a [separate, private repo](https://git.vegvesen.no/projects/NVDBDATA/repos/nvdb-tnits-atlas).

### IntelliJ IDEA Plugins

- [Mermaid Support](https://plugins.jetbrains.com/plugin/20146-mermaid) - For viewing Mermaid diagrams in markdown files.

## TN-ITS Export

### Choices and Assumptions

We set the following TN-ITS fields where there may be ambiguity:

- `validFrom`: Set to the start date of the road feature's first version
- `validTo`, `endLivespanVersion`: Set to the end date of the road feature's last version when closed. For deleted road features, it is set to the date of the export itself.
- `beginLifespanVersion`: Set to the start date of the road feature's current version

For OpenLR, we make the following choices:

- `frc`: Read from road feature 821 (Functional Road Class), with fallback to FRC 7. If multiple road classes exist on the same road link, the one with lowest importance (highest FRC value) is chosen.
- Allowed driving direction: Read from the road link's lane overview, or from road feature 616 (Lane Section) for connecting links.

### Closing and Removal

- When closing a road feature, we set UpdateType to `Modify`, and `validTo` to the end date of the road feature's last version.
- When removing/deleting a road feature, we show how the road feature looked before it was deleted, and we set UpdateType to `Remove`, and `validTo` to the date of the export itself.

#### API Models vs Domain Models

We use domain models for serialized storage and logic, otherwise API models are used directly to reduce boilerplate and mapping code.
