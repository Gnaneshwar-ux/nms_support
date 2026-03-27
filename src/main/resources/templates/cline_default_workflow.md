## Oracle NMS JBot – Workflow (Packaged Default, Updated Policies)

- Project: {{PROJECT_FOLDER}}
- Product (Java root): {{PRODUCT_JAVA_PATH}}
- Decompiled: {{DECOMPILED_FOLDER}}
- Runtime (merged): {{WORKING_DIR}}
- Server Name: {{SERVER_NAME}}

Interaction and execution policy (supersedes prior defaults):
- Modes
  - Explain mode (default): If the user has NOT asked to edit/change code/files, provide a concise, accurate explanation with configuration details and paths. Do not modify files. Do not run commands. Prefer short, structured bullets and summaries to reduce tokens.
  - Edit mode (on explicit request): If the user asks to edit/change code/files, target only the Project folder. Before any write, show a proposed diff and require explicit confirmation. After approval, apply only the confirmed changes.
- Command execution
  - Never auto-run any command. Only execute commands if the user explicitly supplies a command and instructs to run it. Otherwise, present commands as suggestions (not executed).
- Read/write scope
  - Read allowed across Project, Product, Decompiled, and merged runtime folders.
  - Writes allowed only in Project ({{PROJECT_FOLDER}}). Never write to Product or Decompiled.
- Database/NMS_MCP
  - Use the `nms-mcp` server for Oracle DB read-only checks, preferably through `oracle_connect`, `review_sql`, and `execute_sql`.
  - Create DB sessions explicitly with the needed user credentials; do not assume sessions already exist.
  - Plain read-only `SELECT` queries may be safe, but anything beyond obvious read-only access should be reviewed first and explicitly confirmed.
- Response style
  - Be precise, avoid chatter, prefer clear lists, include exact paths/keys/snippets. Keep output lean but complete.

Permissions and safety
- Read allowed across the folders above for analysis.
- Edits allowed only in the Project folder.
- Always show proposed diff and get explicit user confirmation before applying any edit (even in Project).
- Never write to Product or Decompiled folders.
- Never run commands unless the user explicitly provides and asks to execute them.

Oracle DB placeholders
- Host: {{DB_HOST}} | Port: {{DB_PORT}} | SID: {{DB_SID}}
- User: {{DB_USER}} | Password: {{DB_PASSWORD}}
- JDBC URL: {{ORACLE_JDBC_URL}}
- Suggested connect string: {{SQLCL_CONNECT_STRING}}
Note: If using service name, use user/password@//host:port/service.

NMS_MCP Oracle DB usage (pseudocode)
- server_name: nms-mcp; tool: oracle_connect
- args: { "username": "{{DB_USER}}", "password": "{{DB_PASSWORD}}", "connectString": "{{DB_HOST}}:{{DB_PORT}}/{{DB_SID}}" }
- server_name: nms-mcp; tool: execute_sql
- args: { "dbSessionId": "<session-id>", "sql": "SELECT COUNT(*) FROM SOME_TABLE" }
- Policy: read-only by default; any DDL/DML or non-obvious SQL requires explicit user confirmation.
- Troubleshooting note:
  - If NMS server logs show query failures, missing tables/views, synonym errors, or permission issues for read-only/write users, inspect the `.nmsrc` file on the NMS server to discover available database users.
  - You can also validate access using `ISQL` and `ISQL -admin` interactive shells on the server.
  - Suggested workflow: create database connections through NMS_MCP for the relevant users and test queries with the appropriate account.
  - In many development environments, database usernames and passwords are often the same, but always verify from server configuration before use.

## Log Analysis and debugging info - {{SERVER_NAME}}
Use NMS-MCP server to communicate with ssh or sql querying
While working with server at any point question raises just prompt to developer and get confirmed don't run anything without clarity or proper confirmations

Oracle NMS Application logs available at local %temp%/OracleNMS/

Weblogic Host: {{WEBLOGIC_HOST}}
Login: {{LDAP_USER}} + sudo su - gbuora
Logs: This host may have multiple managed servers running. Find all the servers; if a match is found with the server name provided by the user, logs will usually be like {{SERVER_NAME}}.out and {{SERVER_NAME}}.log.
Example: /home/nmsadmin/Oracle/Middleware/Oracle_Home/user_projects/domains/nms/servers/nms_1/logs/nms_1.out

NMS Host: {{NMS_HOST}}:{{NMS_PORT}}
Login: {{LDAP_USER}} + sudo {{NMS_TARGET_USER}}
Logs: This server contains daemon services like isis, JMS, genpublisher, etc. It may also contain multiple projects with different users. If a target user is provided, use that; otherwise list the users folder prompt to confirm or select by the developer. Usually logs are available at ~/logs/, $NMS_HOME/logs/
Example: /home/nmsadmin/logs

Database Host: {{DB_HOST}}:{{DB_PORT}}
Login: {{LDAP_USER}} + sudo su - gbuora
DB schema Credentials: {{DB_USER}}/{{DB_PASSWORD}}
SID: {{DB_SID}}
Logs: This is the host where the actual database is hosted. It is mainly used to initialize database/schema setup, data export/import, and godatapump scripts. It is also commonly used for sysdba access and session management via sqlplus / as sysdba.
Additional DB diagnostics:
- Check `.nmsrc` on the NMS server for available DB users and environment-specific connection details.
- If application errors mention missing tables, views, or synonyms, test the same query with alternate users from `.nmsrc`.
- For access troubleshooting, prefer creating multiple NMS_MCP DB connections and verify which user has the required privileges.

BiPublisher: {{BIPUBLISHER}}
Login: {{LDAP_USER}} + sudo su - gbuora
Logs: Contains BiPublisher deployment logs, especially useful for printing failures in application.
Example: /scratch/gbuora/Oracle/Middleware_BIPUB/Oracle_Home/user_projects/domains/bipub/servers/bi_server1/logs/bipublisher/bipublisher.log



Typical workflow
1) If a project XML exists, it supersedes product XML.
2) Properties: project overrides keys; other keys inherited from product.
3) Validate effective files in {{WORKING_DIR}} (merged runtime).
4) If any configuration depends on DB schema or data, retrieve necessary metadata/sample rows using NMS_MCP Oracle DB tools (read-only). Require explicit confirmation before any writes.
5) Respect modes: Explain by default; Edit only on explicit instruction.

Debug checklist
- Confirm which XML is in effect (Project vs Product) in {{WORKING_DIR}}.
- Verify merged properties contain intended overrides.
- Trace data sources/commands in decompiled code for execution flow (read-only).

---
Update: Editing policy and read scope
- Read allowed across these folders: Project ({{PROJECT_FOLDER}}), Product ({{PRODUCT_JAVA_PATH}}), Decompiled ({{DECOMPILED_FOLDER}}), and merged runtime ({{WORKING_DIR}}).
- Edits allowed only in the Project folder.
- Always show a proposed diff and require explicit user confirmation before applying any edit (including Project files).
- Use NMS_MCP Oracle DB tools (`oracle_connect`, `review_sql`, `execute_sql`) for read-only checks; any write requires explicit confirmation.
- Do not run any commands unless the user explicitly instructs to run a provided command.

---
## Oracle NMS JBot Framework – Project Structure and Workflow
The framework is configuration-driven via XML and properties. Project-specific overrides supersede product defaults; properties are merged so project-defined keys override product values while non-overridden keys are inherited.

---
## Project Folder
Path: {{PROJECT_FOLDER}}

Purpose
- Holds project customizations: XML, properties, custom Java command code, and SQL scripts (under `sql`).
- All edits must be made here (never modify product or decompiled jars).
- To override product XML, copy it into the project path and edit the project copy. The project copy is used instead of the product one.

Mode-aware actions
- Explain mode: Point to the exact file(s) and keys to change. Do not edit.
- Edit mode: Propose a minimal diff for the specific file(s) and apply only after approval.

Analysis scope
- While edits are confined to Project, analyze behavior using inputs from Project, Product, and Decompiled JARs to understand effective runtime behavior.

---
## Product Folder
Path: {{PRODUCT_JAVA_PATH}}

Purpose
- Base product code and default XML/properties shipped with NMS.
- Reference implementation for tools and dialogs.
- When a project XML exists, the project XML supersedes the product XML.
- Product and project properties are merged; project keys override product values while non-overridden keys remain from product.

---
## Decompiled JAR
Path: {{DECOMPILED_FOLDER}}

Purpose
- Reference for backend Java logic (analysis only): XML parsing, command flows, UI init, data sources/commands, etc.
- Do not modify. Use to trace dialog loading, confirm command names/parameters, and map property keys to UI widgets/behaviors.

---
## Merge and Resolution Rules (effective behavior)
- XML override: Project XML supersedes product when present.
- Properties merge: Project values override matching product keys; unspecified keys come from product.
- Build/install merges: Standard process merges project + product into the runtime area {{WORKING_DIR}}. Validate here.

---
## Debugging Process
1. Inspect Project first: presence of project XML and overridden properties.
2. Review Product for baseline behavior when no project XML exists.
3. Consult Decompiled JAR to confirm load order, data sources, command invocation paths.
4. Validate merged runtime output in {{WORKING_DIR}}: which XML is in effect; verify merged properties.
5. Search across all folders: correlate dialog names, widget IDs, command IDs, and properties keys.

---
## Code and Configuration Guidelines
- Edits
  - Make changes only in Project.
  - To override product XML, copy to Project and edit the copy.
  - Add/change only needed properties keys; rely on product defaults for the rest.
  - Always propose diffs and require user confirmation before writing.
- XML structure
  - Follow XSD: {{JBOT_XSD_PATH}}; do not infer structure from non-XML formats.
- Properties typically include: labels, colors, queries, UI mappings, feature flags, and other parameters.
- Cross-platform commands
  - When sharing commands/scripts, note the shell (cmd/PowerShell/Bash) and adapt quoting/paths.
  - Suggestions only; no execution unless explicitly instructed by the user.

---
## Build and Runtime Behavior
- Validation (XML syntax/config checks)
  - Suggested command (not executed): ant config
  - Working directory: {{PROJECT_FOLDER}}/jconfig/
  - Policy: Present as guidance only; do not run. Execute only if the user provides and instructs to run.
- Build/install merge
  - The project configuration is merged with product configuration and installed to the runtime area.
  - Merge specifics: XML override; properties merged with project-overrides precedence.
- Final runtime source
  - The application loads configurations from the merged output in {{WORKING_DIR}}.

---
## Localization and Properties Resolution Tips
- Locale chain honored before fallback (tool_lang_locale.properties → tool_lang.properties → tool.properties).
- Keep project properties minimal—override only what you need. Validate merged properties in {{WORKING_DIR}}.

---
## Troubleshooting Checklist
- Which XML is in effect? Check {{WORKING_DIR}} for project vs product.
- Are properties overrides loading? Open merged properties to confirm overrides.
- Unexpected product behavior? Ensure a project XML exists or add required keys.
- Dialog/data issues? Trace data source/command flow in {{DECOMPILED_FOLDER}}; verify names and IDs.
- Baseline vs project: Diff product XML/properties against project versions to isolate meaningful changes.
- New override? Copy product XML into project path, then edit only the project copy.

---
## Label-to-UI mapping and UI XML discovery
When the visible label text is known but the UI name is not, deduce the UI component via properties and XML:
1) Properties search (project first, then product):
   - Search properties for values matching the label (case-insensitive, tokenized), considering locale chain.
   - Collect candidate keys (e.g., dialog.widget.label, *.labelKey, *.textKey).
2) UI XML correlation
   - Search UI XML (project overrides first) for attributes referencing those keys (labelKey/textKey/propertiesRef).
   - Identify dialog/component IDs and effective file path.
3) Execution flow validation
   - Use {{DECOMPILED_FOLDER}} to confirm bundle/key resolution at runtime.
4) Effective runtime check
   - Confirm discovered UI XML and properties in merged output {{WORKING_DIR}}.

Edits
- Edit only Project properties/XML; never Product or Decompiled content.
- Always propose diffs and require explicit confirmation before applying changes.

---
## AI Model Accuracy and Output Discipline
- Provide exact file paths, dialog names, widget IDs, and short code snippets to anchor findings.
- Include OS and shell when offering command examples; do not execute them.
- Search across Project, {{PRODUCT_JAVA_PATH}}, and {{DECOMPILED_FOLDER}} with case-insensitive, tokenized queries.
- Prefer small, incremental changes with explicit acceptance criteria; share diffs/patches.
- Ground fixes with actual errors, stack traces, and command output when available.
- Keep responses concise and structured to reduce tokens while preserving accuracy.

---
## User-added Workspace Folders
Use this space to document any additional folders you've included in the VS Code workspace beyond Project/Product/Decompiled.
For each folder, describe its purpose so the AI respects it and doesn't attempt to remove or modify it.

- Folder path: <path>
- Purpose: <why it's included>
- Notes: <read-only? scripts? data? any special instructions>

Add as many entries as needed.
