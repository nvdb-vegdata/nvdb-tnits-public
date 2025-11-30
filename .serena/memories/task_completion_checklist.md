# Task Completion Checklist

When completing a coding task, follow these steps:

## 1. Code Formatting
```bash
./gradlew ktlintFormat  # Auto-format code
```

## 2. Code Style Check
```bash
./gradlew ktlintCheck   # Verify formatting
```

## 3. Run Tests
```bash
./gradlew test          # Run all tests
```

For specific test classes:
```bash
./gradlew test --tests="ClassName" --info
```

With test name filtering:
```bash
kotest_filter_tests="*pattern*" ./gradlew test --tests="ClassName" --info
```

## 4. Verify Build
```bash
./gradlew build         # Full build including tests
```

## 5. Check IntelliJ Compilation Errors
- Open IntelliJ IDEA
- Check for any red error markers
- Note: IntelliJ might be out of sync sometimes, so don't try to fix forever
- Make note of persistent compilation errors

## 6. Run Application (if applicable)
```bash
./gradlew run           # Test in auto mode
```

Or test specific modes:
```bash
./gradlew run --args="snapshot"
./gradlew run --args="update"
```

## Important Notes

### Testing Rules
- **NEVER** use wildcards (*) in `--tests` parameter
- **ALWAYS** use exact class names with `--tests`
- Use `kotest_filter_tests` environment variable for test name filtering
- Don't delete tests unless explicitly instructed
- If tests fail and you don't understand why, report it

### Commit Guidelines
- Never include "Generated with Claude Code" or similar
- Never include "Co-authored by Claude" in commit messages
- Follow the existing commit message style in the repository
- Use the pre-commit hook (installed via `./gradlew installGitHooks`)

### Code Quality
- Avoid unnecessary comments
- Make private methods public if they need unit testing
- Write self-documenting code
- Be succinct and to the point