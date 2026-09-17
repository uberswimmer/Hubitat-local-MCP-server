# Read-only Rule Machine structure projection

Use `hub_get_app_config(appId, projection="ruleStructure")` through the existing
`hub_read_apps_code` gateway with the Read master enabled. Enumerate candidates
with `hub_list_rules`. This projection cannot be combined with `pageName`,
`summary=true` or `includeSettings=true`. It performs no wizard navigation or
writes and does not execute a rule.

The response identifies `contract: "hubitat.rm.structure"`, `contractVersion: 1`,
`appId` and `ruleFormat`. Non-RM formats return only that envelope. For RM, it adds:

- `requiredExpression`: available text from the main-page `STPage` href; explicit
  compiled Boolean `hasPredicate=false` yields `not_configured`.
- `triggers`: available text from the `selectTriggers` href.
- `actions`: compiled `actionList` membership/order and exactly one indexed row
  per member. Rows select `actType`/`actSubType` through existing settings helpers.
  Stale settings never establish action membership.
- `localVariables`: non-string names/types, never values from mutable state.

Descriptions use individual string entries from the compiled `actions` map. This
shape is feature-detected: an absent map, unknown entry type or missing row yields
an unavailable description. It never splits the main-page action paragraph or
visits an action editor to manufacture boundaries. No exact firmware, paragraph
count or positional layout is required. Consumers must feature-detect the contract.

`status` is `available`, `unavailable` or `withheld` on source fields/rows. Private
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

Synthetic Spock tests cover operators/markup, direct and gateway dispatch, compiled
order, stale rows, redaction and read failures. The test-hub E2E scenario checks the
contract envelope/order and omitted data. Live availability of individual compiled
description strings and named component fields remains pending commissioning.
The installed production server has not been updated by this change. The existing
BAT read/configuration scenarios apply; exercise the new projection and verify
field status on representative rules after installation. Do not run the CI-only
E2E suite on a personal hub.
