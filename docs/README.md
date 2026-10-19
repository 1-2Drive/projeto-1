# Documentation — Drools Realtime Driving Expert System

This folder documents the **Drools Realtime Driving Expert System** (Maven project `projeto-1`), an academic expert system that drives a simulated car. Driving knowledge is written as rules: speed limits, safe distances, overtaking, pedestrians, traffic lights and signs, road hazards, lane changes and weather. Ten times per second, an inference engine applies the rules to what simulated sensors report. It decides a speed and a lane, and explains why, including why the other options were not chosen.

> **Important.** This documentation was requested on the assumption that the project was a *Prolog-based* expert system. The project has **two interchangeable inference engines**:
> - **Drools** (a Java rule engine, version 8.44), the default;
> - **SWI-Prolog**, running the project's own rule interpreter (`src/main/prolog/`).
>
> Both are hosted by a Java 17 / Spring Boot application, with an Angular web interface and a legacy JavaFX client. All documents describe the actual implementation. SWI-Prolog was **not installed** on the analysis machine: statements about the Prolog engine come from reading its code, and its tests were skipped.

**Version documented:** commit `70082b4` on branch `feat/drools-realtime-driving-expert-system`, analysed on 2026-10-18. This is a refresh of the documentation first written for commit `96d80f7` (2026-10-09). Finding IDs are stable across the two versions; fixed and obsolete findings are kept and marked.

---

## 1. Documents

| Document | Purpose | Intended audience |
|---|---|---|
| [BUSINESS_DOMAIN.md](BUSINESS_DOMAIN.md) | What problem the system addresses, use cases and scenarios, domain concepts, inputs and outputs, the driving rules in plain language, worked examples, and limitations versus intended features | Non-programmers, students, evaluators, domain experts; everyone as an introduction |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | Technologies, directory layout, packages, rule and Prolog file organisation, configuration, how to build, run and test, where to look when changing a feature | Developers and maintainers |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, the two backends, information flow, data representation, the role of each inference engine, per-channel arbitration, interfaces, design decisions, architectural findings (A-01…A-14), discrepancies with older documentation | Developers, maintainers, evaluators |
| [EXECUTION_FLOW.md](EXECUTION_FLOW.md) | Chronological flow from start-up through one 100 ms tick; special situations (several rules, no rule, emergency, missing input, engine failure, engine switch); state diagrams; worked examples from real runs | Developers, maintainers, evaluators |
| [BUSINESS_RULES.md](BUSINESS_RULES.md) | Catalogue of all 119 Drools rules and 6 queries (conditions, outputs, priorities, interactions, measured test coverage), the Prolog correspondence, and the rule consistency audit (R-01…R-26) | Developers, maintainers, evaluators; domain readers for the plain-language parts |
| [TESTING.md](TESTING.md) | Test tools and organisation, commands, results of the tests executed in this analysis, exploratory probes, measured rule coverage, scenario coverage, gaps (T-01…T-20) and proposed tests | Developers, maintainers, evaluators |
| [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md) | Links 64 inferred requirements and 15 scenarios to rules, files, tests and a coverage status | Evaluators, maintainers |
| [GLOSSARY.md](GLOSSARY.md) | Alphabetical definitions of technical and domain terms, for both engines | All readers |

---

## 2. Recommended Reading Order

**General path (first-time reader):**

1. This page.
2. [BUSINESS_DOMAIN.md](BUSINESS_DOMAIN.md): what the system does and does not do.
3. [ARCHITECTURE.md](ARCHITECTURE.md): how it is built and how decisions are selected.
4. [EXECUTION_FLOW.md](EXECUTION_FLOW.md): what happens in each tick, with real examples.
5. [BUSINESS_RULES.md](BUSINESS_RULES.md): every rule, and the audit findings.
6. [TESTING.md](TESTING.md) and [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md): evidence and gaps.
7. [GLOSSARY.md](GLOSSARY.md) whenever a term is unclear.

**Developer about to change the code:**

1. [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md), in particular §11, "Where to look when changing a feature" (every rule exists in Drools and in Prolog).
2. [ARCHITECTURE.md §5](ARCHITECTURE.md#5-action-selection-and-priority-resolution).
3. The relevant section of [BUSINESS_RULES.md](BUSINESS_RULES.md).
4. [TESTING.md §11](TESTING.md#11-regression-risks), "Regression risks".

**Evaluator:**

1. [BUSINESS_DOMAIN.md §3–§4](BUSINESS_DOMAIN.md#3-objectives-and-functional-scope).
2. [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md).
3. [BUSINESS_RULES.md §17](BUSINESS_RULES.md#17-consistency-audit).
4. [TESTING.md §4–§5](TESTING.md#4-test-execution-in-this-analysis).

**Non-programmer:** [BUSINESS_DOMAIN.md](BUSINESS_DOMAIN.md) and [GLOSSARY.md](GLOSSARY.md).

---

## 3. Topics Covered

- **Domain:**
  - autonomous-driving safety decisions on a simulated three-lane looping road;
  - speed limits that take effect at their signs;
  - following distance, time-to-collision and overtaking (including the Portuguese Highway Code rules on passing on the right and before crossings);
  - keep-right, lane changes and road markings;
  - pedestrians and crosswalks;
  - potholes, obstacles and barriers;
  - stop signs and cycling traffic lights;
  - rain, visibility and grip with fuzzy logic;
  - moving off after a stop.
- **Technical:**
  - Java 17, Maven, Spring Boot REST and Server-Sent Events;
  - an inference-backend interface;
  - Drools: stateful session in stream (CEP) mode with a pseudo clock, six entry points, three agenda groups, 119 rules, 6 queries (including backward chaining for lane-change safety), truth maintenance, Allen temporal operators;
  - SWI-Prolog: a persistent process with its own production-rule interpreter (dependency index, refraction, meta-interpreter for backward chaining);
  - arbitration of all proposals per control channel in `DecisionContext`;
  - actuator and physics;
  - Angular presentation layer; JavaFX legacy client.
- **Quality:**
  - rule-by-rule consistency audit, re-checked against the new version;
  - exploratory verification of suspected defects;
  - test inventory and execution results;
  - measured rule-firing coverage;
  - gaps and proposed tests;
  - traceability to inferred requirements;
  - discrepancies between the older documentation and the code.

---

## 4. Key Facts at a Glance

All of the following were verified during this analysis; sources are given in the linked documents.

- **Knowledge base (Drools):** 19 DRL files with 119 rules and 6 queries:
  - 32 perception rules;
  - 1 control rule;
  - 64 rules that propose decisions;
  - 22 rules that keep internal state or intermediate (fuzzy) conclusions.
- **Knowledge base (Prolog):** 107 rules mirroring the Drools decision rules. 74 have the same name; 11 Drools rules are split into one Prolog rule per outcome.
- **Decision selection:** two independent decisions per tick, speed and lane, both made in Java (`DecisionContext`) for either engine:
  - **speed:** mandatory stops and emergencies (priority 60–70) override; otherwise all speed ceilings bind and the lowest wins;
  - **lane:** the highest priority wins;
  - **veto:** braking for a stop vetoes a lane change.
  - Every proposal's outcome is recorded ("why not").
- **Tests:**
  - Backend: 473 JUnit tests run; **218 executed and passed**, **255 skipped** (they need SWI-Prolog), 0 failures.
  - Frontend: 57 Angular unit tests executed, **52 passed and 5 failed**; the failures are in the tests, not caused by the environment.
  - The 18 Playwright browser tests were **not executed**.
  - Measured rule coverage: 111 of the 119 Drools rules fire during the test suite.
- **Fixed since the previous version** (confirmed by re-running the probes):
  - speed limit overridden in rain (R-01);
  - gap lost behind slow vehicles (R-03);
  - overtaking latch (R-05);
  - misread road markings (R-06);
  - no emergency escalation at a continuous line (R-07);
  - stale traffic-light memory (R-08);
  - scenarios that did not show their topic (R-20);
  - most of the late pedestrian reaction (R-04).
- **Most important open findings** (details in [BUSINESS_RULES.md §17](BUSINESS_RULES.md#17-consistency-audit) and [ARCHITECTURE.md §9.2](ARCHITECTURE.md#92-audit-findings)):
  - **R-22:** with no way around a closed barrier, the car reaches it from 70 km/h and above (29.7 km/h at impact from 70, 64.8 km/h from 90).
  - **R-23 / R-02:** a `REDUCE_SPEED` decision can still accelerate the car; in rain on a road without signs, a car cruising at 30 km/h speeds up to about 51 km/h.
  - **R-11:** a pedestrian stop can hide a needed emergency brake for a nearer obstacle (obstacle reached at 27.8 km/h in the probe).
  - **A-07:** the JavaFX client's realtime clock disables emergency TTC braking and makes the car wait forever at a stop sign.
  - **A-14:** the Drools and Prolog knowledge bases are kept equivalent by hand, and without SWI-Prolog their equivalence tests are skipped, not failed.
- **Missing features:** several intended features from the draft project report are not implemented, including time-headway keeping, lane-departure detection, sensor-failure handling, confidence values, intersections and driver monitoring ([BUSINESS_DOMAIN.md §10](BUSINESS_DOMAIN.md#10-confirmed-limitations-and-missing-features)). "Why-not" explanations, previously missing, now exist.

---

## 5. Evidence Conventions

- **Confirmed (static):** follows directly from reading the cited code.
- **Confirmed (execution):** observed by running code — the project's own tests, or exploratory probes run on a copy of the repository ([TESTING.md §5](TESTING.md#5-exploratory-verification-probes)). Probes are not part of the project's test suite. All executions in this analysis used the **Drools** engine.
- **Potential:** plausible from the code but not demonstrated.
- **Inferred requirement:** a requirement reconstructed from the README, the code, or the Portuguese draft report and conceptual diagram. The repository has no approved requirements specification.
- **Status of findings:** Open, Partially fixed, Fixed (at `70082b4`), or Obsolete (the feature was removed).
- **Names:** rule names are quoted exactly as written in the DRL files, e.g. "Reduce speed when exceeding detected limit". Code identifiers and file paths are unchanged.

---

## 6. Other Documentation in the Repository

| Item | Relation to this documentation |
|---|---|
| [`../README.md`](../README.md) | Project README with installation and demo instructions, now including the two rule engines. Mostly accurate. Outdated or inaccurate statements (test counts, `window:length(5)`, entry points in `kmodule.xml`, tick order, "only sensor knowledge", "same rule names" for Prolog) are listed in [ARCHITECTURE.md §9.4](ARCHITECTURE.md#94-discrepancies-with-existing-documentation) |
| [`report-implementation-audit.md`](report-implementation-audit.md) | An earlier audit report, kept unchanged. It predates the Prolog backend and the current arbitration, and several of its claims are contradicted by the code; see [ARCHITECTURE.md §9.4](ARCHITECTURE.md#94-discrepancies-with-existing-documentation) |
| [`../diagrams/`](../diagrams) | Earlier Mermaid diagrams. Diagrams 02, 04 and 08 are outdated (ARCHITECTURE.md §9.4). `first_diagram.md` is a Portuguese conceptual model used as a source of inferred requirements |
| [`../Relatorio_Previo_ENGCIA/`](../Relatorio_Previo_ENGCIA) | Portuguese LaTeX preliminary report. Mostly section headings; the sensors chapter is used as a source of inferred requirements |

---

## 7. Limitations of This Documentation

- **Not run here:**
  - the SWI-Prolog backend and its 255 tests (SWI-Prolog not installed);
  - the full stack (Spring Boot + Angular), the JavaFX client and the Playwright suite.

  Statements about them come from reading the code.
- **Probes:** they show that a behaviour occurs in a specific set-up; they do not measure how often it occurs.
- **Requirements:** they are inferred, because no approved specification exists.
- **Real-world adequacy:** thresholds and physics were not assessed against real-world safety standards.
- **Scope:** only Markdown files under `docs/` were created or changed; no source code, test, configuration or dependency was changed.
