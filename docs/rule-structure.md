# Read-only Rule Machine structure projection

Use `hub_get_app_config(appId, projection="ruleStructure")` through the existing
`hub_read_apps_code` gateway with the Read master enabled. Enumerate candidates
with `hub_list_rules`. This projection cannot be combined with `pageName`,
`summary=true` or `includeSettings=true`. It performs no wizard navigation or
writes and does not execute a rule.

The response identifies `contract: "hubitat.rm.structure"`, `contractVersion: 2`,
`appId` and `ruleFormat`. Non-RM formats return only that envelope. For RM, it adds:

- `requiredExpression`: available text from the main-page `STPage` href; explicit
  compiled Boolean `hasPredicate=false` yields `not_configured`.
- `triggers`: available text from the `selectTriggers` href.
- `actions`: compiled `actionList` membership/order and exactly one indexed row
  per member. Rows select `actType`/`actSubType` through existing settings helpers.
  Stale settings never establish action membership.
- `localVariables`: non-string names/types, never values from mutable state.

Action rows contain selected indexed settings in `fields`. The compiled `actions`
map contains execution objects on the observed hub, not descriptions. It is not
used as text, and neither are condition maps with coincidentally matching numeric
keys. No main-page action paragraph is split and no action editor is visited.
No exact firmware, paragraph count or positional layout is required.

Each selected setting reports one of these shapes:

```json
{"status": "available", "value": false}
{"status": "absent"}
{"status": "withheld"}
```

`available` means the original value passes the selected field's primitive domain,
not that the action is understood or complete. Empty strings, null, false and
missing settings remain distinct. The projection does not infer defaults, invert
Booleans, resolve variable scope, interpret units or reconstruct expressions.
Device lists contain only numeric IDs; variable references must resolve to retained
non-string metadata with local shadowing. Arbitrary strings, nested maps and
unknown enum values are withheld. A consumer must not interpret absent or withheld
fields as false, zero or an unconditional command.

The selected families are switch on/off, buttons, lock/unlock, shade position,
capture/restore, delay/cancellation, repeat timing and structural branch/repeat
identities. Common delay selector, duration, cancellation, randomness and retained
variable-reference fields are included. Field names come from the existing native
RM writer's mappings. Other settings, execution modifiers, expression operands,
wait configuration and variable assignments are not supplied by this revision.
The single downstream semantic parser must report those omissions explicitly.
`getEndRepeat` and `getStopRepeat` remain distinct source subtypes.

Version 2 replaces the unverified version-1 action-description assumption. A v1
consumer must reject it until updated; accepting the new envelope alone is not a
valid migration. This change does not establish new parsed inventory coverage.

`status` is `available`, `unavailable` or `withheld` on components/rows. Private
notification/HTTP/custom/comment/file payloads are withheld and receive a fixed
category. Other unknown action types are opaque. Fields mentioning String/unknown
local or global variables are withheld, respecting local-over-global shadowing.
Credential-like variable names are excluded regardless of type. Missing or unreadable compiled order makes the
action component unavailable; an actual empty list means no active actions.
Required read errors produce `success:false` with a fixed message and no raw error.
Each required endpoint is read once; this does not promise an atomic snapshot.

The projection selects source evidence, not normalized automation semantics.
Callers must still allowlist grammar, remove runtime readings and evaluated text,
validate references/scopes and scan output before persistence. Device labels and
other free text are not guaranteed safe merely because they are in this response.
Never archive raw responses as sanitized inventory or claim a restorable backup.

`stripAppConfigHtml` removes actual tag syntax and script/style bodies before
single-pass entity decoding, preserving literal `<`, `<=`, `>` and `>=` comparisons
adjacent to markup. Missing comparisons must not be guessed from runtime truth.

## Verification status

The owner installed v1. Read-only commissioning found available action-order
lists but no available action descriptions. Values-free diagnostics on two rules
confirmed execution-object records; the other examined maps do not establish an
action-description source. Version 2 removes the unverified text-map assumption.

Synthetic Spock tests use object-shaped execution records and exercise direct and
gateway dispatch, order, stale rows, typed settings selection, redaction, local
shadowing, distinct absence/empty/false values and failures. The test-hub E2E
scenario requires actual duration values in two delay actions separated by a
redacted comment, so an empty rule can no longer pass the substantive source check.
The installed owner's hub still needs v2 commissioning and downstream parser
integration before a fresh inventory can be published. Do not run the CI-only E2E
suite on a personal hub.
