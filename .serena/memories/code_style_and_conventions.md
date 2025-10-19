# Code Style and Conventions

## Formatting
- **Tool**: ktlint with IntelliJ IDEA code style
- **Max Line Length**: 160 characters
- **Wildcards**: Allowed in imports
- **Enum Naming**: Standard naming disabled (allows custom naming)
- **Property Naming**: Flexible (standard naming disabled)

## Logging
- Use SLF4J via the `WithLogger` interface
- Access logger via `log` property
- Use `log.measure("description") { }` for performance logging
- Example:
  ```kotlin
  class MyService : WithLogger {
      fun doWork() {
          log.info("Starting work")
          log.measure("Processing") { /* work */ }
      }
  }
  ```

## Dependency Injection
- **Framework**: Koin with annotations (see docs/KOIN_DEPENDENCY_INJECTION.md)
- Use `@Singleton` for services and repositories
- Use `@Named("name")` to distinguish multiple instances
- Use `@KoinApplication` on the main module object

## Testing
- **Framework**: Kotest
- **Structure**: Arrange, Act, Assert (AAA pattern)
- **Comments**: Only for longer tests where clarification is needed
- **Assertions**: Test for correct outcomes, not failures
- **Private Methods**: Make public instead of using reflection
- **Test Naming**: Test resources follow pattern `vegobjekt-<type>-<id>.json`

## General Guidelines
- Be succinct and to the point
- Avoid unnecessary comments - prefer self-documenting code
- Only add comments that explain non-apparent behavior
- Correct assumptions when they're wrong
- Make private methods public if they need unit testing