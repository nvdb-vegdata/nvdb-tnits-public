# Suggested Commands

## Build and Test
```bash
./gradlew build          # Full build including tests
./gradlew test           # Run tests only
./gradlew clean build    # Clean build
```

## Running the Application
```bash
./gradlew run                        # Auto mode (decides snapshot vs update)
./gradlew run --args="snapshot"      # Generate full snapshot
./gradlew run --args="update"        # Generate delta update
./gradlew run --args="inspire-roadnet"  # Export INSPIRE RoadNet
```

## Code Formatting
```bash
./gradlew ktlintCheck        # Check code style
./gradlew ktlintFormat       # Format code automatically
./gradlew installGitHooks    # Install pre-commit formatting hook
```

## Testing
```bash
# Run all tests
./gradlew test

# Run specific test class (NEVER use wildcards in --tests)
./gradlew test --tests="ExactClassName"

# Filter tests by name using environment variable
kotest_filter_tests="*test name pattern*" ./gradlew test --tests="ExactClassName" --info

# Example:
kotest_filter_tests="*should format with padded vegobjekttype*" ./gradlew test --tests="SpeedLimitExporterTest" --info

# Rerun tests
./gradlew test --rerun
```

## API Model Generation
```bash
./gradlew generateAllApiModels  # Generate models from OpenAPI specs
```

## Docker Services
```bash
docker compose up -d    # Start MinIO and other services
docker compose down     # Stop services
```

## NVDB API Testing and Test Resources

**IMPORTANT:** All fetched NVDB data must be saved to `tnits-generator/src/test/resources/`

### Fetch and Save Test Data

```bash
# Fetch a vegobjekt and save to test resources
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/323113504?inkluder=alle" | jq . > tnits-generator/src/test/resources/vegobjekt-105-323113504.json

# Extract veglenkesekvens IDs from vegobjekt response
# IMPORTANT: Veglenkesekvens IDs are in stedfesting.linjer[].id, NOT lokasjon.veglenkesekvenser
jq -r '.stedfesting.linjer[].id' tnits-generator/src/test/resources/vegobjekt-105-323113504.json | paste -sd, -

# Fetch related veglenkesekvenser and save to test resources
IDS=$(jq -r '.stedfesting.linjer[].id' tnits-generator/src/test/resources/vegobjekt-105-323113504.json | paste -sd, -)
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=$IDS" | jq . > tnits-generator/src/test/resources/veglenkesekvenser-$(echo $IDS | cut -d, -f1)-$(echo $IDS | rev | cut -d, -f1 | rev).json
```

### Complete Workflow Example

```bash
# Fetch speed limit vegobjekt
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegobjekter/105/323113504?inkluder=alle" | jq . > tnits-generator/src/test/resources/vegobjekt-105-323113504.json

# Extract and fetch related veglenkesekvenser
IDS=$(jq -r '.stedfesting.linjer[].id' tnits-generator/src/test/resources/vegobjekt-105-323113504.json | paste -sd, -)
curl "https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/vegnett/veglenkesekvenser?ider=$IDS" | jq . > tnits-generator/src/test/resources/veglenkesekvenser-$(echo $IDS | cut -d, -f1)-$(echo $IDS | rev | cut -d, -f1 | rev).json

# Verify files
ls -lh tnits-generator/src/test/resources/vegobjekt-105-323113504.json tnits-generator/src/test/resources/veglenkesekvenser-*-*.json
```

### Naming Conventions
- Vegobjekter: `vegobjekt-<type>-<id>.json`
- Single veglenkesekvens: `veglenkesekvens-<id>.json`
- Multiple veglenkesekvenser: `veglenkesekvenser-<firstId>-<lastId>.json`
- Always format JSON with `jq`
- Always save to `tnits-generator/src/test/resources/`

## Git Hooks
```bash
./gradlew installGitHooks  # Configure Git to use .hooks folder
```

## macOS Specific Notes
- System is Darwin (macOS)
- Standard Unix commands work (grep, find, ls, cd, etc.)
- Use `jq` for JSON formatting
