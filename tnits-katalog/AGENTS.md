# tnits-katalog

## Project Overview

REST API service that exposes TN-ITS road network data exports from NVDB. Built with Kotlin and Spring Boot.

**Local URL**: http://127.0.0.1:8999/

## Development Workflow

### Running the Application

```bash
# Run with Gradle
./gradlew :tnits-katalog:bootRun

# Run in development with continuous compilation
./gradlew :tnits-katalog:classes -t  # In one terminal
./gradlew :tnits-katalog:bootRun     # In another terminal
```

Spring Boot DevTools is enabled for hot reload of:

- Static resources (HTML, CSS, JS)
- Java/Kotlin classes (requires recompilation)

### Frontend Development

**IMPORTANT**: After making changes to `.html` or `.js` files, you MUST run:

```bash
bunx prettier --write tnits-katalog
```

This ensures consistent code formatting across all static assets.

**Static Files Structure:**

- `src/main/resources/static/index.html` - Landing page with links to API docs and viewer
- `src/main/resources/static/browser.html` - Dataset browser for snapshots and updates
- `src/main/resources/static/viewer.html` - Interactive map viewer for TN-ITS exports
- `src/main/resources/static/browser.js` - Client-side logic for browser page
- `src/main/resources/static/utils.js` - Shared formatting utilities (ES module)
- `src/main/resources/static/common.css` - Shared design system styles

**Frontend Tech Stack:**

- ES Modules with importmap
- Leaflet for map visualization (viewer.html)
- Vanilla JavaScript with Fetch API (no build step)

### Key Files

- `src/main/resources/application.yml` - Configuration (port, paths, etc.)
- `src/main/kotlin/no/vegvesen/nvdb/tnits/katalog/presentation/TnitsController.kt` - REST API endpoints
- `src/main/kotlin/no/vegvesen/nvdb/tnits/katalog/infrastructure/S3FileService.kt` - MinIO/S3 integration

## Testing

When testing the web UI, prefer Chrome DevTools MCP over Playwright MCP for better performance analysis and debugging capabilities.

## Accessibility

All pages follow WCAG 2.1 standards with:

- Proper ARIA labels and live regions
- Screen reader support
- Semantic HTML
- Keyboard navigation support
