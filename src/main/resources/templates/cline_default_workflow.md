# Oracle NMS Workflow Template for Cline / Codex

## Purpose
Use this template as a simple Oracle NMS working guide.

Primary goals:
- stay configuration-first
- prefer evidence over assumptions
- keep remote actions small, readable, and low risk
- separate local config analysis from live-host diagnosis

This template is intentionally light on MCP details.
There is an MCP available for remote host and DB diagnostics.
When remote work starts, ask that MCP for its own usage guidance and live policy instead of assuming its preferred command style.

## MCP quick note
There is an Oracle NMS-oriented MCP available in this workspace for:
- SSH access to NMS, WebLogic, and related Unix hosts
- Oracle DB read-only diagnostics
- NMS guide discovery and cached PDF access
- live usage guidance and live confirmation-policy inspection

Use that MCP as the remote execution layer, not this template.
This template explains what to investigate and in what order.
The MCP explains how to structure commands so they are more likely to auto-run cleanly.

When remote work begins, prefer this order:
1. ask the MCP for usage guidance
2. ask the MCP for live policy if confirmations matter
3. use its target-session SSH flow when you know the project user up front
4. use its DB flow for exact read-only SQL
5. use its NMS guide tools when product documentation or process flow is needed

Configuration and process guides can also be pulled through the MCP.
If the exact Oracle NMS guide, installation flow, administration flow, or configuration process is needed, use the MCP guide-discovery path instead of searching manually.

## Runtime context placeholders
- Project name: `{{PROJECT_NAME}}`
- Project folder: `{{PROJECT_FOLDER}}`
- Product Java root: `{{PRODUCT_JAVA_PATH}}`
- Decompiled reference: `{{DECOMPILED_FOLDER}}`
- Merged runtime output: `{{WORKING_DIR}}`
- NMS env var: `{{NMS_ENV_VAR}}`
- JBot XSD: `{{JBOT_XSD_PATH}}`

- Server name: `{{SERVER_NAME}}`
- NMS host and port: `{{NMS_HOST}}:{{NMS_PORT}}`
- WebLogic host: `{{WEBLOGIC_HOST}}`
- BiPublisher host: `{{BIPUBLISHER}}`

- Login user: `{{LDAP_USER}}`
- Login secret (encoded): `{{LDAP_PASSWORD_ENCODED}}`
- Target user: `{{TARGET_USER}}`
- NMS target user: `{{NMS_TARGET_USER}}`
- Host fallback user: `{{HOST_USER}}`
- Host fallback secret (encoded): `{{HOST_PASSWORD_ENCODED}}`

- DB host: `{{DB_HOST}}`
- DB port: `{{DB_PORT}}`
- DB SID or service ref: `{{DB_SID}}`
- DB schema user: `{{DB_USER}}`
- DB schema password (plain if available): `{{DB_PASSWORD}}`
- DB password (encoded if available): `{{DB_PASSWORD_ENCODED}}`
- JDBC URL: `{{ORACLE_JDBC_URL}}`
- SQLcl connect string: `{{SQLCL_CONNECT_STRING}}`

Optional context to discover when missing:
- In NMS server details of weblogic and DB mentioned in file `.nmsrc`(file exist in corresponding project user home dir)
- NMS config directory, properties files

## Working modes
### Explain mode
Default mode.
Use read-only discovery and explain findings.
Do not change files, services, or DB changes.

### Edit mode
Use only when the user explicitly asks to modify files.

Rules:
- edit only in `{{PROJECT_FOLDER}}`
- never edit `{{PRODUCT_JAVA_PATH}}` or `{{DECOMPILED_FOLDER}}`
- keep overrides minimal
- validate merged runtime after each meaningful config change

### Execute mode
Use only when the user explicitly asks to run operational actions.

Rules:
- prefer read-only checks first
- keep commands small and inspectable
- require explicit confirmation for service changes, restarts, deployments, DDL, DML, or session-kill actions

## Oracle NMS architecture
Treat Oracle NMS as three linked tiers:
1. NMS services tier
2. WebLogic application tier
3. Oracle DB tier

A symptom can appear in one tier and originate in another.
Correlate findings across tiers before concluding.

## Source-of-truth order
Use this order unless the user asks otherwise:
1. project config in `{{PROJECT_FOLDER}}`
2. product baseline in `{{PRODUCT_JAVA_PATH}}`
3. merged runtime in `{{WORKING_DIR}}`
4. decompiled reference only when behavior is still unclear
5. live WebLogic host state and logs
6. DB connectivity, schema, synonyms, and grants
7. live NMS host state and logs

## NMS configuration flow
For most Oracle NMS local configuration work, treat the system as four layers:
1. project overrides in `{{PROJECT_FOLDER}}`
2. product defaults in `{{PRODUCT_JAVA_PATH}}`
3. merged runtime output in `{{WORKING_DIR}}`
4. live runtime state on NMS, WebLogic, and Oracle DB tiers

Use this configuration sequence:
1. identify the functional area
   JBot/UI, service startup, WebLogic integration, datasource/DB wiring, model/runtime parameters, or project SQL
2. locate the project override first
   XML, properties, SQL, include files, or generated project config
3. compare against the product baseline only to understand inheritance
4. validate the effective result in `{{WORKING_DIR}}` - For debuging(Actual code application uses)
5. validate live runtime only after the merged config path is clear

High-value NMS configuration families:
- project XML and include files under `{{PROJECT_FOLDER}}`
- project properties under `{{PROJECT_FOLDER}}`
- project SQL under `{{PROJECT_FOLDER}}`
- In NMS server - `{{NMS_CONFIG}}/jconfig/build.properties`
- In NMS server - `.nmsrc` and environment bootstrap values
- service layout files such as `system.dat`, `system.dat.init`, and `system.dat.model_build`
- runtime parameter SQL and site-parameter SQL

High-value behavior questions and where to start:
- UI/JBot behavior: project XML -> product XML -> merged runtime -> decompiled flow
- Data not showing: Database synonyms and config table data as expected state. datasource mapped tables
- logs showing any errors on this local machine, server or weblogic?

## Configuration-first workflow
Use this sequence for most Oracle NMS questions:

1. Clarify the target area.
   Project config, runtime behavior, WebLogic behavior, database behavior, or operator UI.
2. Check project override presence first.
   If a project XML or properties file exists, assume it wins over product defaults until proven otherwise.
3. Compare with product baseline.
   Use this only to understand inherited behavior, not as the file to edit.
4. Validate merged runtime.
   `{{WORKING_DIR}}` is the effective runtime truth for many XML and properties questions.
5. Go remote only when local config is not enough.
6. Use decompiled code only to explain runtime behavior that config inspection did not settle.

## XML and properties rules
- XML override rule: project XML,INC supersedes product XML,INC when present.
- Properties merge rule: project keys override product keys; missing keys inherit from product.
- SQL execution flow: product sql runs first and project next to each file ex: nms_schema_exmaple.sql runs and next OPAL_schema_example.sql next for a OPAL named project. product sql found at NMS server $NMS_HOME/sql or $NMS_BASE/.. folders
- Decompiled code is read-only reference material.
- Effective runtime truth lives in `{{WORKING_DIR}}`.

Validation order:
1. project override present?
2. product baseline behavior?
3. merged runtime result?
4. Database table or view inplace?
4. decompiled flow if still unclear?

## Local NMS development flow
Use this as the normal local development path for Oracle NMS customization work:

1. understand the request in terms of config area
   JBot, properties, SQL, runtime wiring, service layout, or integration behavior
2. inspect project override files in `{{PROJECT_FOLDER}}`
3. inspect matching product defaults in `{{PRODUCT_JAVA_PATH}}`
4. make the smallest project-only change needed
5. validate merged runtime in `{{WORKING_DIR}}`
6. inspect decompiled references only if runtime behavior is still not obvious
7. use remote host or DB checks only when local config analysis cannot settle the question

Local edit rules:
- keep edits inside `{{PROJECT_FOLDER}}`
- do not edit `{{PRODUCT_JAVA_PATH}}`
- do not edit `{{DECOMPILED_FOLDER}}`
- prefer minimal overrides over copy-heavy duplication
- Make sure every change includes proper comments with requirement ids. 
- keep project comments and requirement references accurate when the file format supports comments

Local validation checklist:
- project override exists where expected
- merged runtime reflects the intended override
- no unintended product-level fallback remains
- related properties keys still resolve correctly
- host-side runtime evidence matches the local config story when remote validation is required

## Remote diagnostics guidance
There is an MCP available for NMS, WebLogic, and Oracle DB diagnostics.
Do not restate its tool contract here.
When remote work begins:

1. ask the MCP for its usage guidance
2. ask the MCP for live policy if confirmation behavior matters
3. keep commands exact and narrow
4. prefer one stable target session over repeating sudo on every command
5. prefer one related batch of standalone checks over one large shell bundle

General remote style:
- exact standalone commands are better than large quoted wrappers
- grep, tail, ps, find, ls, and hostname-style checks should stay simple
- interactive flows are only for real prompts, shell adoption, SQL clients, or streaming tasks
- do not source profile files after sudo unless the command truly depends on them

Practical MCP usage pattern:
- for host work, start with the MCP usage guide and then use its preferred SSH session path
- for repeated target-user diagnostics, prefer one adopted target session over repeated `sudo -u ...`
- for related diagnostics on one host, prefer one related command batch instead of one large shell bundle
- for DB work, prefer the MCP DB session and one exact read-only SQL statement at a time
- for product process or setup guidance, use the MCP documentation tools to fetch the matching NMS guide PDF

## NMS host workflow
Use the NMS host for:
- `.nmsrc`
- runtime environment truth
- service state
- NMS logs
- app-to-DB connection intent

Default sequence:
1. confirm current host and user context
2. confirm whether target-user adoption is needed
3. inspect `.nmsrc` and runtime environment when NMS commands or DB aliases are unclear
4. run service-level status checks such as `smsReport`
5. inspect focused recent logs before widening the search
6. inspect processes only after log and service state suggest a runtime issue

High-value NMS-side items:
- `{{NMS_HOME}}/.nmsrc`
- `{{NMS_CONFIG}}/jconfig/build.properties`
- service logs under the runtime log path
- runtime references to DB users, JDBC aliases, and WebLogic targets

Best connection style:
- login as `{{LDAP_USER}}`
- if repeated project-owned checks are needed, adopt `{{NMS_TARGET_USER}}`
- if only one quick read-only check is needed, stay with the login user unless the file permissions or runtime path require the project user
- do not switch users repeatedly inside one investigation unless the target context truly changes

## WebLogic workflow
Use the WebLogic host when the issue is about:
- managed server liveness
- domain layout
- datasource startup problems
- JMS/JNDI deployment issues
- managed-server log evidence

Default sequence:
1. find the domain home
2. confirm the managed server names
3. inspect recent `{{SERVER_NAME}}.log` or `{{SERVER_NAME}}.out`
4. inspect process list only after log context is clear
5. confirm listen address, ports, and startup arguments from the host itself when needed

Trust model:
- WebLogic host is the source of truth for server identity, listen address, and port details
- NMS config is the source of truth for how the project expects to reach WebLogic

Best connection style:
- login as `{{LDAP_USER}}`
- adopt `{{TARGET_USER}}` when logs, domain scripts, or managed-server ownership require it
- stay on one adopted session for the whole log and process review instead of repeating privilege switches
- prefer direct log and process evidence from the WebLogic host over inferred status from elsewhere

## Database workflow
Use the DB tier to confirm:
- schema split
- synonym targets
- grants and privileges
- read-only vs read-write datasource intent
- object existence

Before opening a DB session:
1. identify the intended connect target from `.nmsrc` or merged config
2. prefer the read-only application schema for diagnostics
3. avoid jumping to admin accounts unless the user explicitly needs that level

Preferred SQL style:
- one exact read-only statement at a time
- prefer `SELECT ...` or `WITH ... SELECT ...`
- keep row counts and projections small
- do not use multi-statement SQL for normal diagnosis

Recommended DB sequence:
1. basic connectivity check
2. confirm current schema and target database identity
3. inspect user, synonym, object, and grant relationships
4. compare RO and RW usage only when the symptom depends on account choice

Best connection style:
- prefer Oracle client connectivity through the MCP DB session over OS-level DB host login when the question is schema, synonym, grant, or read-only query related
- prefer `{{DB_USER}}` for diagnostics unless the user explicitly requires admin-level investigation
- use DB host shell access only when the problem is wallet, listener, OS, storage, or DB-host runtime related

## Host reference quick guide
### NMS host
- Host: `{{NMS_HOST}}:{{NMS_PORT}}`
- Preferred access pattern: login as `{{LDAP_USER}}`, then adopt `{{NMS_TARGET_USER}}` when repeated project-user checks are needed
- Typical logs: NMS runtime logs and service logs

### WebLogic host
- Host: `{{WEBLOGIC_HOST}}`
- Preferred access pattern: login as `{{LDAP_USER}}`, then adopt `{{TARGET_USER}}` when repeated domain and log checks are needed
- Typical logs: `<domain_home>/servers/*/logs`

### DB host
- Host: `{{DB_HOST}}`
- Preferred access pattern: use host access only when the DB issue cannot be answered from NMS-side config plus Oracle client access
- Preferred app schema: `{{DB_USER}}`

### Local development machine
- Project root: `{{PROJECT_FOLDER}}`
- Product root: `{{PRODUCT_JAVA_PATH}}`
- Effective runtime root: `{{WORKING_DIR}}`
- Preferred access pattern: solve locally first, then validate remotely only when local config and merged runtime are not enough

## Risk model
### Low risk
- local config reads
- merged-runtime inspection
- focused host diagnostics
- focused read-only SQL

### Medium risk
- broader log sweeps
- performance-heavy diagnostics
- wide metadata queries

### High risk
- service restarts
- deployment scripts
- cache refresh commands with service impact
- DDL, DML, DB admin actions
- session kills

## Confirmation rules
- low-risk diagnostics can proceed when the user asked for diagnosis
- medium-risk actions should be scoped narrowly and explained first
- high-risk actions always require explicit confirmation

When asking for confirmation, include:
- exact command or SQL
- target host, user, or schema
- one-line consequence summary

Never self-confirm.

## NMS/WebLogic/DB issue playbooks
### Service or runtime issue
1. confirm service status
2. inspect recent relevant logs
3. inspect process evidence only if logs point there
4. correlate with DB or WebLogic dependency if needed

### WebLogic app issue
1. confirm server and domain
2. inspect server log
3. inspect datasource or deployment errors
4. correlate with DB connectivity and NMS-side wiring

### DB or ORA issue
1. capture exact error text
2. confirm active schema and target object
3. inspect synonyms and grants
4. compare intended RO and RW mappings if relevant

### Config mismatch issue
1. inspect project override
2. inspect product baseline
3. inspect merged runtime
4. trace decompiled behavior only if still unresolved

## High-value files and locations
- project XML and properties under `{{PROJECT_FOLDER}}`
- product XML and properties under `{{PRODUCT_JAVA_PATH}}`
- merged runtime under `{{WORKING_DIR}}`
- `.nmsrc`
- `{{NMS_CONFIG}}/jconfig/build.properties`
- WebLogic domain `servers/*/logs`
- project SQL and parameter scripts under `{{PROJECT_FOLDER}}`
- service layout files and startup control files in the runtime config area

## Oracle NMS guides and process references
If installation flow, administration flow, configuration steps, or command/process guidance is needed:
- use the MCP documentation tools to discover the right Oracle NMS version
- resolve the exact guide PDF through the MCP cache path
- use that guide as the source of truth for product process details

Typical reasons to consult guides through MCP:
- environment setup flow
- service startup or administration process
- WebLogic integration steps
- model build or deployment process
- customer load or operational workflow explanation

## Output contract
Always provide:
1. what was found
2. what it means
3. confidence and uncertainty
4. next smallest safe step if unresolved

## Workspace extension notes
Use this section to document extra folders added to workspace beyond Project/Product/Decompiled.

- Folder path: `<path>`
- Purpose: `<why included>`
- Notes: `<read-only? scripts? data? constraints>`

Add entries as needed.
