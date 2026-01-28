## Oracle NMS JBot – Workflow (Packaged Default)

- Project: {{PROJECT_FOLDER}}
- Product (Java root): {{PRODUCT_JAVA_PATH}}
- Decompiled: {{DECOMPILED_FOLDER}}
- Runtime (merged): {{WORKING_DIR}}

Permissions and safety:
- Read allowed across the folders above for analysis.
- Edits allowed only in the Project folder.
- Always show proposed diff and get explicit user confirmation before applying any edit (even in Project).
- Never write to Product or Decompiled folders.

Oracle DB placeholders:
- Host: {{DB_HOST}} | Port: {{DB_PORT}} | SID: {{DB_SID}}
- User: {{DB_USER}} | Password: {{DB_PASSWORD}}
- JDBC URL: {{ORACLE_JDBC_URL}}
- SQLcl connect: {{SQLCL_CONNECT_STRING}}
Note: If using service name, use user/password@//host:port/service.

SQLcl MCP usage (pseudocode):
- server_name: sqlcl; tool: execute/query
- args: { "connect": "{{SQLCL_CONNECT_STRING}}", "sql": "SELECT COUNT(*) FROM SOME_TABLE" }

Typical workflow:
1) If a project XML exists, it supersedes product XML.
2) Properties: project overrides keys; other keys inherited from product.
3) Validate effective files in {{WORKING_DIR}} (merged runtime).
4) If any configuration depends on DB schema or data, retrieve the necessary metadata/sample rows using the SQLcl MCP server (if available) with {{SQLCL_CONNECT_STRING}} built from the provided DB credentials. Keep reads non-destructive; require explicit confirmation before any writes.

Debug checklist:
- Confirm which XML is in effect (Project vs Product) in {{WORKING_DIR}}.
- Verify merged properties contain intended overrides.
- Trace data sources/commands in decompiled code for execution flow (read-only).
---
Update: Editing policy and read scope
- Read allowed across these folders: Project ({{PROJECT_FOLDER}}), Product ({{PRODUCT_JAVA_PATH}}), Decompiled ({{DECOMPILED_FOLDER}}), and merged runtime ({{WORKING_DIR}}).
- Edits allowed only in the Project folder.
- Always show a proposed diff and require explicit user confirmation before applying any edit (including Project files).
- Use SQLcl MCP tools (execute/query) with connect "{{SQLCL_CONNECT_STRING}}" for read-only checks; any write requires explicit confirmation.

---
## Oracle NMS JBot Framework – Project Structure and Workflow
The Oracle NMS JBot framework follows a configuration-driven UI defined via XML and properties files. Project-specific overrides supersede product defaults when present, and properties are merged so project-defined keys override product values while non-overridden keys are inherited.

---
## Project Folder
Path: {{PROJECT_FOLDER}}

Purpose:
- Holds all project-specific customizations:
  - XML configuration
  - Properties files
  - Java command code for custom commands
  - SQL scripts for project-specific database customizations (under `sql`)
- All edits must be made here (do not modify the product folder or decompiled jars).
- To override a product XML, copy the product XML into the corresponding project path and edit it there. The project copy will be used instead of the product one [3].

Analysis scope:
- While edits are confined to the Project Folder, always analyze behavior using inputs from all folders (Project, Product, and Decompiled JARs) to understand effective runtime behavior.

---
## Product Folder
Path: {{PRODUCT_JAVA_PATH}}

Purpose:
- Contains base product code and default XML/properties shipped with NMS.
- Serves as the reference implementation for all tools and dialogs.
- When a project version of an XML exists in the project folder, the project XML supersedes the product XML [3].
- Product and project properties are merged; project keys override product values while any non-overridden keys remain sourced from the product properties [6].

---
## Decompiled JAR
Path: {{DECOMPILED_FOLDER}}

Purpose:
- Reference copy of backend Java client/server-side logic for analysis only:
  - XML parsing and command execution flows
  - UI initialization/launch sequences
  - Data source and command implementations
  - If `cesejb.jar` is included, it provides server-side EJB/client interaction references
- Do not modify this code. Use it to:
  - Trace how a dialog loads its XML and data sources
  - Confirm command names, parameters, and error handling
  - Map properties keys to UI widgets and behaviors

---
## Merge and Resolution Rules (effective behavior)
- XML override:
  - If a project XML exists, it supersedes the product XML for that component/dialog. Typical workflow is to copy the product XML into the project path and then edit the project copy [3].
- Properties merge:
  - Product and project properties are concatenated into a generated/merged output. Project values override the same keys from product; keys not defined by the project continue to come from product [6].
- Build/install merges:
  - The standard NMS process merges project configuration with product configuration and places the results into the runtime/working area used by the applications [2]. Use this merged output for validation and debugging.

---
## Debugging Process
1. Inspect the Project Folder first:
   - For a given dialog or tool, check whether a project XML exists. If it does, that file supersedes the product XML [3].
   - Check project properties for overridden keys affecting labels, queries, behaviors, or feature flags [6].
2. Review the Product Folder for baseline behavior:
   - When there is no project XML, the product XML defines the behavior.
   - Compare product vs. project properties to see which keys the project overrides vs. which are inherited [6].
3. Consult the Decompiled JAR for execution flow:
   - Confirm how the framework locates and loads XML/properties, the data sources used, and command invocation paths.
   - Use class and method names to align stack traces with configuration.
4. Validate the merged runtime output in {{WORKING_DIR}}:
   - Confirm which version of each XML is present (project vs. product).
   - Inspect generated/merged properties to verify the final values taking effect (project overrides present; product defaults retained for non-overridden keys) [6].
5. Search strategy across all folders:
   - Use case-insensitive substring searches and tokenized queries.
   - Correlate dialog names, widget IDs, command IDs, and properties keys across project, product, and decompiled code.

---
## Code and Configuration Guidelines
- Edits:
  - Make changes only in the Project Folder.
  - To override product XML, copy it into the project path and edit the project file [3].
  - Add or change only the properties keys you need; rely on product defaults for the rest [6].
  - Always prompt with a diff and require explicit user confirmation before writing any change.
- XML structure:
  - Follow the XSD for structure/validation: {{JBOT_XSD_PATH}}
  - Do not infer XML structure from non-XML formats.
- Properties files typically include:
  - Labels, colors, queries, UI mappings, feature flags, and other parameters [6].
- Cross-platform commands:
  - When sharing commands/scripts, note the shell (cmd/PowerShell/Bash) and adapt quoting/paths/flags accordingly.

---
## Build and Runtime Behavior
- Build and Validation (XML Syntax Safety Check)
- Allowed anytime – no confirmation required
- Purpose: Validate XML syntax and configuration correctness early. 
- Command: 'ant config'
- Execution directory: {{PROJECT_FOLDER}}/jconfig/
- Safe to run: Before edits, After edits and Repeatedly during debugging
- Build/install step:
  - The project configuration is merged with product configuration and installed to the working/runtime area used by clients/servers [2]. Inspect this area to validate the effective configuration.
- Merge specifics:
  - XML: project file supersedes product when present [3].
  - Properties: merged; project values override corresponding product keys; non-overridden keys are inherited from product [6].
- Final runtime source:
  - The application loads configurations from the merged output in:
    - {{WORKING_DIR}}

---
## Localization and Properties Resolution Tips
- Properties resolution honors locale-specific files before falling back to more general ones (e.g., tool_lang_locale.properties → tool_lang.properties → tool.properties) [6].
- Keep project properties minimal—override only what you need. Validate final generated/merged properties in {{WORKING_DIR}}.

---
## Troubleshooting Checklist
- Which XML is in effect? Check {{WORKING_DIR}} to see if a project XML exists for the dialog (if present, it supersedes product).
- Are your properties overrides loading? Open generated/merged properties to confirm your keys override as expected.
- Unexpected product behavior? Ensure a project XML exists where needed, or add missing properties keys.
- Dialog/data issues? Trace data source and command flow in {{DECOMPILED_FOLDER}}; verify data source names and command IDs.
- Baseline vs project: Diff product XML/properties against project versions to isolate meaningful changes.
- New override? Copy product XML into project path, then edit the project copy only.

---
## Label-to-UI mapping and UI XML discovery
When the visible label text is known but the UI name is not provided, deduce the UI component via properties and XML:
1) Properties search (project first, then product):
   - Search all relevant properties files for values matching the visible label text (case-insensitive, tokenized).
   - Consider locale chain (tool_lang_locale.properties → tool_lang.properties → tool.properties).
   - Collect candidate keys (e.g., dialog.widget.label, *.labelKey, *.textKey).
2) UI XML correlation:
   - Search UI XML files (project overrides first, then product) for attributes referencing those keys (e.g., labelKey/textKey/propertiesRef).
   - Identify the dialog/component IDs and the file path in effect. If a project XML exists, it supersedes product.
3) Execution flow validation:
   - Use {{DECOMPILED_FOLDER}} to confirm how bundles/keys are resolved at runtime (resource bundle loading, getString(key), mapping to widgets).
4) Effective runtime check:
   - Confirm the discovered UI XML and properties are present in the merged output {{WORKING_DIR}}.
Edits:
- Make changes only in Project properties/XML; never edit Product or Decompiled content.
- Always propose diffs and require explicit confirmation before applying changes.

---
## AI Model Accuracy Tips
- Provide exact file paths, dialog names, widget IDs, and short code snippets to anchor searches.
- Include OS and shell (cmd/PowerShell/Bash) when asking for commands.
- Search across Project, {{PRODUCT_JAVA_PATH}}, and {{DECOMPILED_FOLDER}} using case-insensitive, tokenized queries.
- Prefer small, incremental changes with explicit acceptance criteria; share diffs/patches.
- Include actual errors, stack traces, and command output to ground fixes.
- When paths vary, provide both forward/backslash forms if helpful.
