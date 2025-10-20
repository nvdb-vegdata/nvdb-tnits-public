# tnits-katalog

## Project Overview

REST API service that exposes TN-ITS and INSPIRE road network data exports from NVDB. Built with Kotlin and Spring Boot.

**Local URL**: http://127.0.0.1:8999/

## Key Files

- `src/main/resources/static/index.html` - Landing page with links to API docs and viewer
- `src/main/resources/application.yml` - Configuration (port, paths, etc.)

## Testing

When testing the web UI, prefer Chrome DevTools MCP over Playwright MCP for better performance analysis and debugging capabilities.
