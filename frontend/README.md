# Frontend

Angular 18 application for the Intelligent Driving Decision Support System.

This app is **presentation-only**. It renders backend world coordinates, sensor
readings, Drools decisions, listener timelines, Working Memory and explanations,
and it sends commands to the Spring Boot API. It never evaluates driving policy.

## Prerequisites

- Node.js 20.x (validated with 20.20.2) and npm 10.x (validated with 10.8.2).
- The Spring Boot backend running on `http://localhost:8080`.

## Install

```bash
npm ci
```

## Development server

```bash
npm start
```

Serves `http://localhost:4200/` and proxies `/api` to the backend via
`proxy.conf.json`. Hot reload is enabled.

## Tests

```bash
npm test
```

Karma + Jasmine in headless Chrome (`ng test --watch=false --browsers=ChromeHeadless`).

## Production build

```bash
npm run build
```

Output goes to `dist/frontend`.

## End-to-end tests

The E2E suite runs against the **real** stack (real Angular app, real Spring Boot
API, real SimulationEngine, real sensors, real Drools session, real WebSocket channel).
Start the backend and this dev server first.

```bash
npm run e2e
```

Useful overrides:

```bash
# custom output directory
npm run e2e -- --output=/tmp/projeto1-playwright

# single browser executable
PLAYWRIGHT_CHROMIUM_EXECUTABLE="/path/to/chrome" npm run e2e
```

## Source layout

```text
src/app/
  core/          REST client, WebSocket client, models, signal-based state
  simulation/    page composition, road canvas, world state
  scenario/      scenario toolbox, element configuration, scenario library
  telemetry/     vehicle, speed advisor, sensors
  expert-system/ decision, working memory, inference timeline, explanation
  shared/        status badge, flow stage
```
