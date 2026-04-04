# Oracle NMS Workflow Template for Cline / Codex

## Purpose
Use this template as the default decision and safety layer for Oracle NMS troubleshooting and configuration work across Project, Product, Decompiled, NMS host, WebLogic host, and Oracle DB.

Primary goal:
- Maximize correctness and evidence quality.
- Minimize operational risk and unnecessary changes.
- Keep output concise, structured, and actionable.

## Runtime context placeholders
- Project name: `{{PROJECT_NAME}}`
- Project folder: `{{PROJECT_FOLDER}}`
- Product Java root: `{{PRODUCT_JAVA_PATH}}`
- Decompiled reference: `{{DECOMPILED_FOLDER}}`
- Merged runtime output: `{{WORKING_DIR}}`
- NMS env var: `{{NMS_ENV_VAR}}`
- JBot XSD: `{{JBOT_XSD_PATH}}`

- Server name: `{{SERVER_NAME}}`
- WebLogic host: `{{WEBLOGIC_HOST}}`
- NMS host and port: `{{NMS_HOST}}:{{NMS_PORT}}`
- Login user: `{{LDAP_USER}}`
- Login secret (encoded): `{{LDAP_PASSWORD_ENCODED}}`
- Target user: `{{TARGET_USER}}`
- NMS target user: `{{NMS_TARGET_USER}}`
- Host fallback user: `{{HOST_USER}}`
- Host fallback secret (encoded): `{{HOST_PASSWORD_ENCODED}}`
- BiPublisher host: `{{BIPUBLISHER}}`

- DB host and port(default 22): `{{DB_HOST}}`
- DB Host Login user: `{{LDAP_USER}}`
- DB Host password (encoded): `{{DB_PASSWORD_ENCODED}}`
- DB SID or service ref: `{{DB_SID}}`
- DB schema user: `{{DB_USER}}`
- DB schema password (plain, if available): `{{DB_PASSWORD}}`
- JDBC URL: `{{ORACLE_JDBC_URL}}`
- SQLcl connect string: `{{SQLCL_CONNECT_STRING}}`

Optional context to discover when missing:
- NMS home path from runtime/user profile or `.nmsrc` (for example: `<NMS_HOME>`).
- NMS config directory (for example: `<NMS_CONFIG>`).
- WebLogic domain home (for example: `<WEBLOGIC_DOMAIN_HOME>`).
- WebLogic admin URL (for example: `<WEBLOGIC_ADMIN_URL>`).

## System architecture snapshot
Treat Oracle NMS as three linked tiers:
1. NMS Services tier (Unix service/process layer).
2. WebLogic tier (Java/EJB/application server layer).
3. Oracle DB tier (data and privilege layer).

A fault in one tier can surface in another tier, so always correlate evidence across layers.

## Operating modes
### 1) Explain mode (default)
Use read-only discovery and explain findings. Do not change files, services, or DB state.

### 2) Edit mode (explicit)
Enabled only when user explicitly asks to modify files.
Rules:
- Edit only in `{{PROJECT_FOLDER}}`.
- Never edit `{{PRODUCT_JAVA_PATH}}` or `{{DECOMPILED_FOLDER}}`.
- Show proposed diff before applying.
- Apply only approved edits.

### 3) Execute mode (explicit and risk-aware)
Enabled only when user explicitly asks to run commands or operations.
Rules:
- Prefer read-only checks first.
- Classify risk severity before execution.
- Require confirmation for high-impact actions.

## Decision engine
Use this routing for every request:
1. Clarify objective: discovery, explanation, edit, or execution.
2. Identify scope: Project, Product, Decompiled, NMS host, WebLogic host, DB.
3. Choose safest path that answers the question.
4. Gather evidence in small steps, one question per command/query.
5. Summarize findings and confidence.
6. If inconclusive, propose the next minimal command set.

Escalation logic:
- If read-only evidence is enough, stop there.
- If change is required, present exact change/command and impact.
- If risk is ambiguous, treat as higher severity and ask before running.

## Discovery priority order
Use this fixed order unless the user asks otherwise:
1. Local config in `{{PROJECT_FOLDER}}` and `{{PRODUCT_JAVA_PATH}}`.
2. Effective merged runtime in `{{WORKING_DIR}}`.
3. NMS host runtime state and logs.
4. WebLogic host state and managed-server logs.
5. Database connectivity, schema/synonym/privilege checks.

## Unknown-detail recovery and source-of-truth map
Use this section when details are missing, conflicting, or possibly stale.

### NMS server: primary source for DB connection intent and merged project config
Check:
- `{{NMS_HOME}}/.nmsrc` (or project-generated `.nmsrc`)
- wallet/TNS setup created by `nms-env-config`
- merged runtime config files

Key DB-related entries to verify in `.nmsrc` and related config:
- `RDBMS_ADMIN`
- `RDBMS_HOST`
- `ORACLE_READ_WRITE_USER`
- `ORACLE_READ_ONLY_USER`
- TNS alias and wallet mapping used by `ISQL` and `ISQL -admin`

Interpretation rule:
- Admin schema is separate from read/write and read-only access schemas.
- Access schemas are typically synonym-based.

### NMS server: WebLogic-related project configuration
Check:
- `{{NMS_CONFIG}}/jconfig/build.properties`
- generated runtime configuration files

High-value keys:
- `client.url`
- `config.datasource`
- `publisher.ejb-user`
- `config.ws_runas_user`
- `config.multispeak_runas_user`
- `weblogic.version`

Map/CORBA-related parameter family to verify:
- `WEB_corbaInitRef`
- `WEB_intersysName`
- `WEB_mapDirectory`
- `WEB_mapHttpdPort`
- `WEB_mapHttpdHost`
- `WEB_syncMaps`
- `WEB_tempDirectory`

### DB side: what it can and cannot confirm
DB can reliably confirm:
- CORBA naming reference values.
- read/write vs read-only schema split and synonym intent.

DB cannot reliably confirm:
- WebLogic managed-server host identity.
- WebLogic listen address and listen ports.

If NMS-side WebLogic checks fail or are inconclusive:
1. Switch to WebLogic host directly.
2. Check `AdminServer` and managed-server processes.
3. Check domain and managed-server logs.
4. Check managed-server startup arguments.

Important context:
- `nms-wls-config create-server` typically automates managed-server creation, JDBC connectivity, JMS, T3, and startup arguments.
- Therefore WebLogic-domain config is the source of truth for server-name, host, and port details.

### Practical source-of-truth rule
- NMS server: `.nmsrc` and merged project runtime configuration truth.
- DB: schema/parameter and access-model truth.
- WebLogic host: server identity, listen address, and port truth.

### Where-to-look-first decision tree
1. If DB user/schema/connect details are unknown: start on NMS server (`.nmsrc` + wallet mapping).
2. If datasource/multispeak/publisher run-as behavior is unknown: inspect `{{NMS_CONFIG}}/jconfig/build.properties` and merged runtime config.
3. If CORBA/map bindings are unclear: verify `WEB_*` parameter family and DB-side values.
4. If managed-server host/port/server-name are uncertain: switch to WebLogic host and verify domain/process/log/start-arg truth directly.
5. If sources conflict: prefer WebLogic host for host/port/server identity, DB for schema/access truth, NMS merged config for project wiring intent.

## Risk model for commands and SQL
### Severity 1: local/read-only low risk
Examples:
- `hostname`, `whoami`
- `smsReport`
- `ps -ef | grep ... | grep -v grep`
- `grep -n`, `tail`, `find ... | sort | tail`
- read-only SQL against known objects

### Severity 2: read-only broader scope
Examples:
- multi-host status checks
- larger log sweeps
- metadata-style SQL across broader schemas

### Severity 3: state-adjacent diagnostics
Examples:
- cache refresh or reload-like actions
- heavier diagnostics that may impact performance

### Severity 4: operational change with meaningful blast radius
Examples:
- subsystem start/stop
- redeploy steps
- environment-level config refresh
- DB maintenance operations with possible service effect

### Severity 5: disruptive or destructive
Examples:
- full restart, failover, rollback
- DB DDL/DML in production-like systems
- session kill, purge, mass update, schema object changes

## Confirmation policy
- Severity 1-2: proceed when user asked for diagnostics.
- Severity 3: confirm unless user explicitly requested that exact action.
- Severity 4-5: always require explicit confirmation.

When asking confirmation, include:
- exact command/query set,
- target host/user/schema,
- one-line consequence summary.

Never self-confirm.

## Missing-information protocol
If critical context is missing, do not guess high-impact actions.
Collect minimum required context in this order:
1. Exact host and target user.
2. Scope of issue (NMS service, WebLogic app, DB, config behavior).
3. Time window and concrete error text.
4. Desired outcome (diagnose only, edit, restart, redeploy, DB change).

If still ambiguous, pause and ask for only the missing critical detail.

## NMS MCP-first remote diagnostics policy
Use `nms-mcp` as primary for remote shell and Oracle DB diagnostics.

Preferred tool selection:
- Single remote check: `execute_command`
- Related check set: `review_command_batch` + `execute_command_batch`
- Interactive-only scenarios: `start_interactive_command`
- Oracle DB: `oracle_connect` -> `review_sql` -> `execute_sql`

Execution style:
- Prefer short standalone commands over large bundled shell blocks.
- Keep each command focused on one diagnostic question.
- Continue next safe read-only check if one check fails.
- Prefer inspectable output (`head`, `tail`, `grep -n`, sorted lists).

Credential handling:
- Use encoded secret fields when provided.
- Never print raw or decoded secrets.
- Do not leak secrets in summaries, logs, or plans.

### Oracle NMS documentation tools (MCP)
These docs tools are part of the existing `nms-mcp` server (not a new MCP server), implemented via `nms-docs.ts` and `index.ts`.

Available tools:
- `list_nms_guides`
- `get_nms_guide_pdf`

What these tools do:
- Scrape the live Oracle NMS docs library for available versions and guide names.
- Resolve guide PDF URLs automatically from Oracle docs pages.
- Download and cache PDFs locally.
- Return local cached PDF paths so AI can read docs without manual browsing/downloading.

Cache location:
- Default: `C:\Users\<you>\Documents\nms-docs\<version>\*.pdf`
- Example: `C:\Users\<you>\Documents\nms-docs\251200\G49134.pdf`

Recommended docs workflow:
1. Call `list_nms_guides` to discover versions and guide names.
2. Call `get_nms_guide_pdf` with selected `version` and `guide`.
3. Use returned local file path as the source for reading/searching the guide.

If NMS MCP docs tools are unavailable:
- Tell the user the NMS MCP setup may be missing or outdated.
- Recommend configuring/updating NMS MCP from the latest DevTools setup flow.
- Explain benefit briefly: faster Oracle-guide access, cached PDFs, and better evidence-driven troubleshooting.

## Intelligent troubleshooting flow
### A) Universal triage
1. Confirm host and user context.
2. Confirm service/app health (`smsReport`, process checks).
3. Locate relevant logs and extract recent errors.
4. Classify issue: service, config, integration, database, performance.
5. Branch to a targeted playbook below.

### B) Service-down playbook
1. Validate daemon/service status.
2. Inspect process list for missing or defunct processes.
3. Inspect latest service logs for startup failure signatures.
4. Check dependency services and ports.
5. Only then propose restart/redeploy if necessary.

### C) WebLogic/app access playbook
1. Confirm managed server identity and host.
2. Check server status and recent `{{SERVER_NAME}}.out` or `{{SERVER_NAME}}.log`.
3. Validate datasource/JNDI/JMS/SSL mapping for the failing feature.
4. Correlate app errors with backend service or DB dependency.

### D) ORA-/DB error playbook
1. Capture exact Oracle error and failing SQL context.
2. Validate schema, synonym target, object existence, and grants.
3. Compare behavior with alternate `.nmsrc` users where relevant.
4. Verify datasource identity uses expected user (RO vs RW).
5. Propose least-privileged corrective path.

### E) JBot config mismatch playbook
1. Check if project XML exists for that dialog/tool.
2. If project XML exists, it supersedes product XML.
3. Verify property override keys in project properties.
4. Validate merged runtime result in `{{WORKING_DIR}}`.
5. Trace code path in `{{DECOMPILED_FOLDER}}` if behavior is still unclear.

## Guarded operational runbooks
Use these only when user explicitly asks for operational action.

### Redeploy runbook (Severity 4-5)
1. Confirm target host/user and deployment scope.
2. Confirm pre-checks: current status, active issues, and recent errors.
3. Show exact redeploy command sequence and expected effect.
4. Run only after explicit confirmation.
5. Validate service and logs immediately after completion.

### Service restart runbook (Severity 5)
1. Confirm exact service set to stop/start.
2. Show exact stop and start command set with blast radius.
3. Approval checkpoint (mandatory): do not execute until explicit user approval for restart.
4. Execute approved commands only.
5. Validate post-start status and logs before closing incident.

Example flow (approval required):
```bash
# APPROVAL REQUIRED BEFORE ANY SEVERITY 5 ACTION
# Wait for explicit user approval to run restart commands.
sms-stop -ais
# wait until stop is fully complete
sms-start
```

### DB session kill runbook (Severity 5)
1. Confirm exact SID/SERIAL or session identifier.
2. Show exact SQL statement to kill target session.
3. Approval checkpoint (mandatory): do not execute until explicit user approval for DB kill.
4. Execute only the approved target session kill statement.
5. Recheck session state after execution.

Example flow (approval required):
```sql
-- APPROVAL REQUIRED BEFORE ANY SEVERITY 5 ACTION
-- Wait for explicit user approval before running this command.
ALTER SYSTEM KILL SESSION 'sid,serial#';
```

## Local configuration structure and merge
Core directories:
- `{{PROJECT_FOLDER}}` for project overrides and customizations.
- `{{PRODUCT_JAVA_PATH}}` for base product defaults.
- `{{DECOMPILED_FOLDER}}` for read-only reference logic.
- `{{WORKING_DIR}}` for effective merged runtime behavior.

High-value file patterns:
- XML definitions: `{{PROJECT_FOLDER}}/**/*.xml`
- Properties overrides: `{{PROJECT_FOLDER}}/**/*.properties`
- Project SQL: `{{PROJECT_FOLDER}}/sql/*.sql`

Merge behavior:
- XML: project overrides product when project XML exists.
- Properties: merged, with project keys taking precedence.
- Runtime truth: validate effective output under `{{WORKING_DIR}}`.

Validation order:
1. Check project override presence.
2. Check product baseline.
3. Confirm behavior path in decompiled references.
4. Validate effective files and keys in merged runtime.

## Oracle NMS JBot resolution rules
- XML override rule: project XML supersedes product XML when present.
- Properties merge rule: project keys override product keys; missing keys inherit from product.
- Effective runtime truth lives in merged output at `{{WORKING_DIR}}`.
- Decompiled artifacts are read-only references for runtime flow tracing.

Safe edit guidance:
- Edit only project files in `{{PROJECT_FOLDER}}`.
- Keep overrides minimal and targeted.
- Follow schema from `{{JBOT_XSD_PATH}}`.
- Validate merged output after changes.

Localization resolution tip:
- Resolve locale-specific properties before generic fallback:
  - `tool_lang_locale.properties`
  - `tool_lang.properties`
  - `tool.properties`

## Host reference quick guide
### NMS host
- Host: `{{NMS_HOST}}:{{NMS_PORT}}`
- Login pattern: `{{LDAP_USER}}` then `sudo su - {{NMS_TARGET_USER}}` (or approved target user)
- Note: Target user is not required to switch if file or log reading is the only performing.Command execution require to switch target user.
- Typical logs: `~/logs`, runtime logs under NMS service directories

### WebLogic host
- Host: `{{WEBLOGIC_HOST}}`
- Login pattern: `{{LDAP_USER}}` then `sudo su - {{TARGET_USER}}` (or approved target user)
- Note: Target user is not required to switch if file or log reading is the only performing.Command execution require to switch target user.
- Typical logs include managed server `.out` and `.log` matching `{{SERVER_NAME}}`
- Validate per-server domain path before deep log scan

### DB host
- Host: `{{DB_HOST}}:{{DB_PORT}}`
- Login pattern: `{{LDAP_USER}}` then `sudo su - {{TARGET_USER}}` (or approved target user)
- Note: Target user is not required to switch if file or log reading is the only performing.Command execution require to switch target user.
- SID/service ref: `{{DB_SID}}`
- Preferred app schema user: `{{DB_USER}}`
- Connection reference:
  - JDBC: `{{ORACLE_JDBC_URL}}`
  - SQLcl: `{{SQLCL_CONNECT_STRING}}`

### BiPublisher
- Host: `{{BIPUBLISHER}}`
- Use for print/report deployment diagnostics when relevant

## WebLogic discovery and direct fallback rule
Primary discovery from NMS side:
- inspect NMS runtime config and `.nmsrc`-linked settings for app server mapping.

Direct discovery on WebLogic host:
- `ps -ef | grep weblogic`
- list managed servers under domain `servers/` folder when available.

Fallback rule:
- if NMS-side commands cannot reliably confirm WebLogic state, trust direct host checks over inferred NMS status.

## NMS startup/stop behavior cues
- `sms-start` starts service groups from configured system definition.
- `sms-stop` stops clients/services in controlled sequence.
- `sms-start -f <file>` may use alternate service definitions.
- After stop operations, inspect process state before any restart if hung/defunct processes are suspected.

## Database intelligence checklist
When DB behavior is part of the issue, verify in this order:
1. Active schema/user used by failing component.
2. Object ownership and synonym chain.
3. Grants/privileges for intended operation.
4. RO/RW datasource mapping correctness.
5. NLS or environment mismatches.

If failures mention missing tables/views/synonyms:
- inspect `.nmsrc` for alternate valid users,
- test same read-only SQL with candidate users,
- identify least-privileged valid account.

Common schema role pattern to keep in mind:
- Admin schema: object ownership and setup/patch operations.
- RW application schema: daily DML and operational access.
- RO schema: restricted read-only access for safe querying.
- Integration schemas: minimal privilege and task-specific synonym access.

## DB access methods and example read-only SQL
Common access paths:
- `ISQL` for application-level checks.
- `ISQL -admin` for controlled admin-level checks.
- `sqlplus / as sysdba` only when explicitly approved and necessary.

Example read-only queries:
```sql
SELECT username FROM all_users;
SELECT * FROM all_synonyms WHERE owner = '{{DB_USER}}';
SELECT * FROM user_tables;
SELECT 1 FROM dual;
```

## Recommended read-only command patterns
Use examples as references and adapt per host.

Context:
- `hostname`
- `whoami`

Service health:
- `smsReport`
- `nms-isis status`
- `ps -ef | grep <name> | grep -v grep`

Logs:
- `find <log_dir> -type f | sort | tail -n 20`
- `grep -i -n "ERROR|Exception|ORA-" <log_file> | tail -n 20`
- `tail -n 200 <log_file>`

Ports:
- `ss -ltn | grep <port>`

## System validation quick checks
NMS service state:
- `smsReport`

WebLogic process state:
- `ps -ef | grep weblogic`

DB basic connectivity:
- `SELECT 1 FROM dual;`

## Log location map
- NMS logs: `$NMS_HOME/logs` (or project-specific runtime log path).
- WebLogic logs: `<domain_home>/servers/*/logs`.
- DB logs: Oracle alert log and listener log paths for the target environment.

## Output contract for future responses
Always provide:
1. What was found (facts only).
2. What it means (impact on NMS/WebLogic/DB behavior).
3. Confidence and any uncertainty.
4. Next smallest safe action if unresolved.

For risky actions:
- show exact command/query,
- include target host/user/schema,
- include one-line blast radius,
- wait for explicit confirmation.

# Oracle NMS configuration workflow for Cline

This is a generic implementation guide for Oracle Utilities Network Management System (NMS). It is written to help with project-specific configuration work while staying close to the product structure used in the Oracle guides.

## How to use this document

Treat the following order as the normal workflow:

1. Set the environment and service layout.
2. Generate or update the model configuration files.
3. Load or rebuild the network model.
4. Load customer data and customer-to-network mappings.
5. Configure operator tools such as Switching, Work Agenda, and Control Tool.
6. Rebuild client configuration and validate the result.

Some file names, tables, and generated outputs are release-specific. In every section below, the “project-specific” note marks the places that normally change from one implementation to another.

---

## 1) Switching configuration structure, crucial files, and flow

Switching configuration is split between the model, the server-side runtime, and the Java client configuration. The model determines which devices exist and how they are classified. The server-side configuration decides which switching actions can run. The client-side configuration decides which buttons, labels, and dialogs appear to the user.

### Main pieces

**Server/runtime flow**
- `system.dat` controls which services start in the operational environment.
- `SwService` is the service that executes switching sheets and automatic switching-related actions.
- `system.dat.model_build` is used while the model is still being built and only starts the minimal service set.
- `CES_PARAMETERS` contains many switch-related runtime parameters and feature toggles.

**Model/configuration inputs**
- `NMS Distribution Modeling workbook` produces the model configuration files.
- `Control Tool Workbook` produces the control-action SQL used by switching and device operation steps.
- `SwmanParameters.properties` contains global Web Switching parameters.
- `schematic_options` stores schematic-related options when switching work requires a schematic representation.

**Client/UI inputs**
- The Web Switching UI is driven by JBot / XML configuration.
- `Control.xml` maps buttons and menu items to control actions.
- `Project_Control_Actions.inc` is commonly used to centralize project-specific control action definitions.

### Key tables

- `CONTROL_ACT`
- `CONTROL_AGGREGATES`
- `CONTROL_ACT_PROMPTS`
- Switching-related configuration tables used by Web Switching and Web Safety

### Flow

1. Define the device classes and the switching model in the model workbook.
2. Generate the runtime model files and control-action SQL.
3. Load the switching configuration into the database.
4. Map buttons, actions, and prompts in `Control.xml` and related JBot definitions.
5. Let `SwService` execute switching sheets and related automatic operations.
6. Validate the result from the Viewer, Switching sheets, and the event log.

### Project-specific notes

- The actual button set differs by utility and by licensed modules.
- Some actions are reused by Web Switching, Web Safety, and the Control Tool.
- Keep the action labels and the device-class inheritance consistent, or the wrong buttons will appear.
- Use project-specific device-class groupings so that the `when` clauses stay readable.

---

## 2) Work Agenda column configuration and logic flow

Work Agenda is mostly a client configuration problem, but it depends on the shape of the event data that comes from the server. The product guide shows the Work Agenda configuration files under the base config path and separates text, columns, and state logic.

### Main pieces

- `WORKAGENDA_GLOBAL_PROPERTIES.inc`
- `WorkAgenda_en_US.properties`
- `WORKAGENDA_TBL_WA_ALARMS.inc` and related XML include files
- `JOBS`
- `UNTIED_OUTAGES`
- `GENERIC_EVENT_FIELDS`
- the Work Agenda data source tables and event-state filters

### Flow

1. The server creates or updates event rows.
2. Work Agenda reads those rows and applies the configured state filters.
3. `WORKAGENDA_GLOBAL_PROPERTIES.inc` controls general behavior such as icons, default ERT offsets, completed-event visibility, and reading-pane delays.
4. `WorkAgenda_en_US.properties` controls labels such as column headings.
5. If you add a project-specific column, the database and client config must be updated together.
6. The client config is rebuilt and then validated in Web Workspace.

### Crucial column logic

- State filters drive whether a row is considered completed, in progress, incomplete, or to-do.
- The reading pane is driven by selection and a configurable delay.
- Completed outages can be hidden or made visible by configuration.
- The grouping dialog can copy selected columns into the event-grouping datastore.

### Project-specific notes

- Adding a new visible Work Agenda column is not only a label change. The new field also has to exist in the server-side tables.
- When a project adds custom event fields, those fields usually need to be added to `JOBS`, `UNTIED_OUTAGES`, and `GENERIC_EVENT_FIELDS`, then the services restarted.
- Display names, sort order, and column widths are usually project-specific.

---

## 3) Network Model configuration, commands, and file flow

The network model is the core of NMS. It starts with source data, gets transformed into model build files, then is merged into the live operations model by MBService.

### Main source inputs

- GIS or CAD/AMFM source data
- `NMS Distribution Modeling workbook`
- `Oracle DMS Power Flow Engineering Data workbook`
- project-specific preprocessors and postprocessors

### Core generated files

- `[project]_classes.dat`
- `[project]_inheritance.dat`
- `[project]_schema_attributes.sql`
- `[project]_attributes.sql`
- `[project]_devices.cel`
- `[project]_landbase.cel`
- `[project]_declutter.sql`
- `[project]_pf_symbology.sql`
- `[project]_SYMBOLS.sym` or the SVG symbol set under `jconfig`
- `[project]_control_zones.dat`
- `[project]_licensed_products.dat`

### Core model tables

- `NETWORK_COMPONENTS`
- `NETWORK_NODES`
- `OBJECT_INSTANCES`
- `ALIAS_MAPPING`
- `DIAGRAM_OBJECTS`
- `POINT_COORDINATES`
- `CES_CUSTOMERS`
- `CUSTOMER_SUM`

### Build flow

1. Extract GIS/CAD source data.
2. Preprocess it into model import files (`.mb` or equivalent).
3. Run the model build service to merge the patch into the operations model.
4. Generate map files for the Viewer.
5. Rebuild symbols, schematics, and any derived data.

### Useful commands and scripts
- These all are high impact commands do not run at all, until user forces it to run, use only for model explaination and understanding.
- `nms-setup`
- `nms-post-setup`
- `nms-model-build`
- `nms-build-maps`
- `nms-mb-setup`
- `nms-make-symbols`
- `nms-sym-to-svg`
- `nms-svg-populate-properties`
- `LatLong`
- `DBCleanup`
- `nms-delete-map`
- `nms-delete-object`
- `nms-delete-patch`

### Power-flow related data files

- `[project]_pf_sources.sql`
- `[project]_pf_line_catalog.sql`
- `[project]_pf_line_limits.sql`
- `[project]_pf_switches.sql`
- `[project]_pf_load_data.sql`
- `[project]_pf_xfmrtypes.sql`
- `[project]_pf_xfmrtaps.sql`
- `[project]_pf_xfmrlimits.sql`
- `[project]_pf_capacitors.sql`
- `[project]_pf_hourly_load_profiles.sql`
- `[project]_pf_dist_gen_data.sql`
- `[project]_grid_derms_contracts.sql`

### Project-specific notes

- The source order is usually GIS first, then workbook overrides, then defaults.
- The `CEL` explosion rules are often the most project-specific part of the entire build.
- Coordinate systems, NCG rules, and symbology mappings usually vary by project.
- If the project uses DMS features, the engineering data workbook becomes mandatory in practice.

---

## 4) Control Tool configuration flow

The Control Tool is the bridge between the model and the actual operator actions. It is configured by database actions, button mappings, and JBot definitions.

### Main pieces

- `CONTROL_ACT`
- `CONTROL_AGGREGATES`
- `CONTROL_ACT_PROMPTS`
- `Control.xml`
- `Project_Control_Actions.inc`
- the Control Tool workbook
- the generated `project_control.sql`

### Flow

1. Define the action in `CONTROL_ACT`.
2. Define ordered multi-step sequences in `CONTROL_AGGREGATES`.
3. Define any user prompts in `CONTROL_ACT_PROMPTS`.
4. Map the action to a button or popup item in `Control.xml`.
5. Add the matching JBot action name.
6. Generate or load the SQL into the project schema.
7. Validate the button visibility by device class and inheritance.

### Important behavior

- `CONTROL_AGGREGATES` must not include SCADA actions in the middle of a sequence when the response model requires one-at-a-time handling.
- The first matching control action wins, so ordering matters.
- Device-class inheritance is the cleanest way to keep the control map maintainable.

### Project-specific notes

- New device classes usually mean new visibility rules.
- New actions often require both a database insert and a UI mapping change.
- The labels should stay short and consistent with the operating terminology used by the utility.

---

## 5) Customer loading process and customer/device mapping structure

Customer loading turns CIS-style customer data into the NMS customer model. The key idea is the link between customer records and the supply node in the electrical model.

### Main tables

- `CU_CUSTOMERS`
- `CU_SERVICE_LOCATIONS`
- `CU_METERS`
- `CU_SERVICE_POINTS`
- `CU_DERS`
- `CES_CUSTOMERS`
- `CES_CUSTOMERS_HISTORY`
- `CUSTOMER_SUM`
- `NMS_ACCOUNTS_HISTORY`
- `supply_nodes`

### Relationship structure

- `CU_CUSTOMERS` holds the account/customer.
- `CU_SERVICE_LOCATIONS` holds the premises or service location.
- `CU_METERS` holds the meter records.
- `CU_SERVICE_POINTS` ties together customer, service location, meter, account type, and the supply node.
- `CU_DERS` adds DER information attached to a meter when needed.
- `CES_CUSTOMERS` is the flat operational view used by services such as JMService and Web Call Entry.
- `CUSTOMER_SUM` is a smaller summary view used for faster outage impact calculations.

### Core mapping rule

The most important relationship is the link between `cu_service_points.device_id` and `supply_nodes.device_id`. That is what ties the customer model to the electrical network model.

### Loading flow

1. Populate the `CU_*` tables from CIS or a project-specific extract.
2. Populate the mirror `CU_*_CIS` staging tables.
3. Run `nms-update-customers` to detect changes and update the NMS model.
4. Rebuild `CES_CUSTOMERS`.
5. Rebuild or refresh `CUSTOMER_SUM` if the project depends on it.
6. Let JMService and related tools consume the updated summaries.

### Project-specific notes

- Some deployments use one meter per location; others use multiple meters per account.
- DER support is optional and may be injected during preprocessing even when the source CIS does not provide it directly.
- Customer priority flags, user-defined fields, and the amount of history retained are usually project-specific.
- If the supply-node mapping is weak, trouble calls become fuzzy and outage reporting loses accuracy.

---

## 6) Other configuration-guide items worth including in a workflow

These are the pieces that usually matter when a project is being implemented, upgraded, or debugged.

### Environment and startup

- `.nmsrc` stores the core runtime environment variables.
- `nms-env-config` updates `.nmsrc` and the wallets used for database and app-server credentials.
- `CES_PARAMETERS` and the `[project]_parameters.sql` / `[project]_site_parameters_*.sql` files store site and environment parameters.
- `system.dat`, `system.dat.init`, and `system.dat.model_build` define the service startup layout.
- `sms-start`, `sms-stop`, `nms-all-start`, `nms-all-stop`, and `nms-wls-control` are the common operational scripts.

### Authentication and WebLogic

- `nms-wls-config` prepares the WebLogic side for certificate-based control.
- `nms-wls-control` starts, stops, and checks managed servers.
- Dual-environment deployments use separate ports and a model-sync filter.

### History and storage

- ILM uses partitioning for active and historical model tables.
- `PURGE_HISTORY_TABLES` is the standard history cleanup procedure.
- `RETAIN_HISTORY_RECORDS` in `CES_PARAMETERS` controls online history retention.

### High-value runtime services

- `SMService` supervises the service tree.
- `DBService` serves database access.
- `ODService` caches static object data.
- `DDService` manages dynamic runtime state.
- `MTService` maintains topology.
- `JMService` handles outage analysis and restoration resources.
- `MBService` builds the model.
- `SwService` runs switching and automatic restoration flows.
- `DMSService` runs power-flow and DMS work.

### Project-specific notes

- Most implementation issues come from mismatches between the workbook, the SQL files, and the runtime service layout.
- If a configuration change affects a service cache, restart or recache the related service instead of changing only the database.
- Keep the base config and the project override files aligned; that reduces upgrade pain later.

---

## Validation checklist

- Confirm the service layout starts cleanly.
- Confirm `isis` is up before running model utilities.
- Confirm `ISQL` works as both the read/write and admin users.
- Confirm the generated model files exist under `OPERATIONS_MODELS`.
- Confirm `CES_CUSTOMERS` and `CUSTOMER_SUM` have the expected rows.
- Confirm the Work Agenda columns and Control Tool buttons match the device classes.
- Confirm switching actions appear only for the intended device classes.
- Confirm the Viewer loads the expected symbols and coordinate system.

---

## Practical rule of thumb

Use the workbook and generated SQL as the source of truth for project differences. Keep runtime files (`system.dat`, `.nmsrc`, `CES_PARAMETERS`, `Control.xml`, Work Agenda overrides) small and deliberate, and avoid mixing model changes with UI-only changes.


## Workspace extension notes
Use this section to document extra folders added to workspace beyond Project/Product/Decompiled.

- Folder path: `<path>`
- Purpose: `<why included>`
- Notes: `<read-only? scripts? data? constraints>`

Add entries as needed.
