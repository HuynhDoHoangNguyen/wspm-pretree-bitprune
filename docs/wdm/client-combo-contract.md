# Client combo contract — HISTORY_PREFIX_V1

## Selection and available stock — owner decision 2026-11-11

Latest is labelled **Top Combo mới nhất**, retains five public same-order bundles
in publication order and displays 🔥 #1..#N plus an explicit detail-popup link.
Individual product names in that popup navigate to the corresponding product.
The source product ID is forwarded through inline cards, popup links and the
direct Combo route. For same-order itemsets, that product stays fully visible;
other unselected products use reduced opacity, selected products use full
opacity. No flame is attached to the context product in the detail section.

`product.availableQuantity` is a required nullable decimal integer string:
sum of `on_hand_quantity - reserved_quantity` across ACTIVE warehouses. This is
an application policy approved with the implementation plan, not a mining
formula. Zero means no available units; null means no active inventory data.
The existing grouped JDBC item query reads stock for all returned items without
per-item HTTP calls. Missing stock is never defaulted to an invented amount.

Checkboxes start unchecked; UI quantities start at 1 independently of persisted
COMBO_ITEM quantities. Unchecked quantities are disabled. Checked quantities must
be exact integers in [1, availableQuantity]; unknown/zero stock cannot be selected.
Only selected valid quantities contribute to exact decimal current-price totals.
No selection yields 0. Combo/context changes and reopening a popup start fresh.
Sequential/mixed selectable rows remain restricted to the server-verified next
itemset. The visible **Thêm vào giỏ hàng** button is always disabled, has no
callback or supplementary note. Reads never reserve stock or write a cart/order.

Stock is the state observed by the read request, not a reservation or checkout
guarantee. No schema migration or mining/ranking/threshold changes are involved.

## Draft persistence limit — owner decision 2026-11-11

Native preview accepts optional integer `draftLimit` in [1,1000]. The current UI
always sends it (default 20), independently of preview `k`, anchor and customer
`recommendationK`. After eligibility and structural grouping, select global Top-N
by existing SUPPORT_THEN_PATTERN_WEIGHT_V1: exact support descending, pattern
weight descending, UTF-8 canonical pattern, numeric pattern ID. No per-kind quota
is implied. Mining output and metric formulas are unchanged.

Preview returns both `groups` (all valid groups) and `plannedGroups` (selected
count). Persistence receives only selected groups/fields and reproduces that
selection from the signed context on submit, reconcile and review. Changing the
limit invalidates preview and requires a new write identity. Omitted limits and
historical contexts preserve full-group persistence; explicit null/non-integral
or out-of-range limits are rejected at the HTTP boundary. No schema migration.

Reproducible catalog-based CSV, standalone SQL seed, demo accounts and manual
steps: [Combo demo](../demo/combo-demo/SEEDER2.md). This section supersedes older
statements that every new UI request always persists all eligible groups.

Owner authorization: client recommendation plan accepted 2026-11-10.
This is an application policy (implementation inference), not a change to mining
support, weights, generation, pruning or the frozen algorithms.

## Publication and public reads

### Current discovery policy — compact Combo popup

The owner authorized separate public bundles and personal temporal suggestions.
Public top-K/product discovery, latest and public detail now expose only
`SAME_ORDER_BUNDLE`. Repository kind filtering occurs before ranking/LIMIT;
latest retains `published_at DESC, combo_id ASC` and at most five entries.
The five-entry Latest limit is the owner's 2026-11-11 correction: with at least
five eligible published bundles, all five appear directly. Latest cards omit the
duplicate linked Combo title and same-order badge; product names, prices and
images remain. Other client same-order cards omit the kind badge too; temporal
kind labels remain available.
Eligibility/publication checks remain unchanged for all three kinds. Temporal
combos remain publishable but are read through authenticated recommendations.

`GET /api/v1/recommendations/combos/personalized?comboId=...` accepts exactly one
combo ID, mutually exclusive with `productId`. Authentication precedes parameter
processing. It returns one `HISTORY_PREFIX_V1` match in the existing envelope,
or 404 for an unavailable/nonmatching combo. This exact lookup verifies current
eligibility and the authenticated identity's history independently of list top-K.
Only next-step suggested product IDs are selectable; query input never supplies
completed steps, another user ID or suggested product IDs. Responses are no-store.

Cards open a compact Modal over the current page. The popup revalidates reads,
shows current product prices, quantities, checkboxes and the exact selected total,
and omits mining/provenance metadata. Public 404 may resolve through authenticated
exact lookup; other failures remain retryable errors. Token/identity changes hide
old data immediately, cancel requests and discard late responses. Modal close,
Escape and browser Back preserve the underlying page/filter state. Existing
`/combos/:comboId` links render the same modal with a product/list return path.
No cart, checkout, order write or implicit combo discount is implemented.

Product-detail personal suggestions suppress a matched recommendation when its
next-step `suggestedProductIds` contains the currently viewed product. Apply this
context filter before K, and mirror it defensively in the client. Suppress the
whole recommendation rather than splitting a mixed same-order step. Global
personal suggestions, public same-order bundles and history matching are unchanged.

This section supersedes earlier statements below permitting public temporal
discovery/detail and the older full-page technical detail UI. The history matcher,
publication/review rules, mining outputs and earlier evidence are unchanged;
earlier acceptance results describe their historical scope.

Verification on 2026-11-10:

- Focused backend unit/controller/security/mapping/matcher checks: 76 passed.
  `ComboManagementMySqlTest`: 11 passed on owned disposable MySQL, including
  public same-order filtering, exact personal lookup and cross-identity denial.
- Full frontend suite: 83 suites / 1132 tests passed; two opt-in live tests
  skipped. TypeScript and production build passed with existing warnings.
- Production UI with mocked HTTP: Edge 1440px/390px passed public, sequential
  and mixed popup selection, guest denial, exact totals, Back/Escape, preserved
  query and absence of business writes/browser errors. Sidebar navigation smoke
  also passed both viewports. This does not claim full native mining E2E rerun.
- The local backend was rebuilt/restarted only after a read-only check found no
  queued/running mining rows. Flyway reported V5 up to date and no migration.
  Live anonymous reads returned latest 200/empty, personal 401 and temporal
  public detail 404. No owner-data seed/review/publication was performed.
- Evidence is retained under ignored
  `.agent-workspace/combo-popup-139eafec94394a26992245f4b6d37e59/`:
  `frontend-results.json`, `mysql.log`, `build-verified.log`, `browser/result.json`,
  `navigation/result.json`, screenshots and backend startup/package logs.
  `backend-before.jar` retains the previous runtime artifact. The preliminary
  failed-build directory remains after automatic cleanup review returned
  `blocked by policy`; no pre-existing source or evidence was deleted.

Only explicitly APPROVED combos in a DRAFT batch may be published by ADMIN.
Existing transactional review authority, freshness, source and catalog checks
remain mandatory. Activation archives the previous ACTIVE batch atomically.
All three known kinds are supported. Approval alone never publishes a combo.

Public reads require PUBLISHED + ACTIVE, valid_from <= now < valid_to (null is
unbounded), a single primary source in the batch's run and matching weight policy.
Every item must still have ACTIVE product/category and a nonnegative price.
Sequential/mixed items must exactly reproduce the primary PATTERN_ITEM positions
through RUN_ITEM_STATISTIC product mapping; itemsets/positions are not flattened.
Product-context discovery is not evidence of a buyer having completed a prefix.
It may show any constituent product, with the complete ordered steps labelled.

## History matching

The authenticated server identity selects CUSTOMER.user_id; no caller user ID.
Use exact COMPLETED orders, ordered by ordered_at UTC then order_id code points,
matching the accepted dataset-csv-contract. One order is one distinct itemset;
equal timestamps do not merge orders. Quantity does not multiply occurrences.

For sequential/mixed combos, greedily match the longest leading prefix of whole
pattern itemsets as a subsequence of the ordered history. Each pattern itemset
must be a subset of one order's products. Different pattern steps require strictly
different, increasing orders. Unrelated orders may occur between matched steps.
Earliest-match greedy is valid for subset subsequence existence. No matched first
step, or all steps already matched, produces no personal recommendation.

Recommend only products in the next itemset, excluding every product already
present anywhere in this buyer's completed history. If nothing remains, suppress
that recommendation. SAME_ORDER_BUNDLE is product-context discovery only; a past
purchase does not establish a same-order basket. No temporal probability or
confidence is inferred from support. Return matched step count and suggested
product IDs, never raw orders, customer IDs or history of another identity.

Rank matching results by existing SUPPORT_THEN_PATTERN_WEIGHT_V1, then canonical
pattern and combo ID; apply K after eligibility/history matching. Read candidates
in bounded pages, batch item reads, without a pre-matching top-K truncation.
No qualifying history yields an empty personal result; public discovery remains
available and explicitly labelled. Latest cards use published_at DESC, combo ID
as tie-break; 'latest' is not sales popularity.

## HTTP

- GET /api/v1/recommendations/combos?productId=...&k=1..20: public context.
- GET /api/v1/recommendations/combos/{comboId}: public complete steps.
- GET /api/v1/recommendations/combos/latest: public newest five eligible same-order combos.
- GET /api/v1/recommendations/combos/personalized: Bearer required; optional single
  productId scopes combo membership before matching/ranking/K (never a user ID);
  returns policy, evaluatedAt and up to four ranked matches, matchedItemsets,
  nextItemsetOrder, suggestedProductIds and public combo detail.

Private responses use Cache-Control: no-store. Reject unknown/duplicate query
parameters. Authenticate before personal input/resource processing. On logout,
identity/token replacement, unmount or product change, abort/ignore old responses
and hide old identity data immediately. Do not automatically replay mutations.

## Client

Product list: URL-backed name/category/price/sort/page, collapsible mobile filter,
two newest public combos, separately labelled personal suggestions.
Product detail: real catalog fields, images, at most two compact selectable combo
cards INSIDE the right information column, then related ACTIVE products below.
Personal matches take precedence; public matches fill remaining slots after
deduplication by combo ID. Mobile places these cards after product information.
Successful empty recommendation/latest sections render nothing, including their
headings. Failures remain visible and retryable instead of pretending to be empty.
Sequential selections use only the next step when personal; context discovery
labels steps and does not claim the viewer purchased previous steps.
Selected totals use exact decimal current prices times quantity, without an
implicit full-combo discount. Selection is not checkout or order creation.

## Acceptance

Verification date: 2026-11-10. Development data was not reset, seeded or published
by this task. E2E writes used isolated synthetic resources only.

| Criterion | Result | Evidence |
|---|---|---|
| CLIENT-AC01 Manual publication, ADMIN, one active batch | PASS | ComboManagementMySqlTest; native E2E approval/publication |
| CLIENT-AC02 All three kinds retain itemsets/steps | PASS | MySQL source projection, API validator tests, real two-engine native E2E |
| CLIENT-AC03 Identity-bound personal history | PASS | PersonalComboControllerTest, MySQL cross-identity test, real registered customer E2E |
| CLIENT-AC04 No incorrect order/itemset match | PASS | HistoryPrefixMatcherTest, 500 reproducible tiny comparisons with exhaustive embedding; MySQL mixed-prefix test |
| CLIENT-AC05 Guest/cold-history public discovery | PASS | Native anonymous API/browser and empty Admin personal result |
| CLIENT-AC06 Product list/header/filter/latest | PASS | Catalog routing/list tests, URL persistence and 1440/390px E2E |
| CLIENT-AC07 Detail/selection/total/related | PASS | Component and catalog tests; real detail screenshot and checkbox E2E |
| CLIENT-AC08 Expired/archived/ineligible excluded | PASS | MySQL inactive-product test, native expiry/archive HTTP cases restored in finally |
| CLIENT-AC09 Stale session/response and responsive | PASS | Held real response after logout, component abort/identity tests, desktop/mobile screenshots |
| CLIENT-AC10 Regression/build/mining scope | PASS within stated scope | 96 focused backend tests; 1118 frontend tests; build, E2E and unchanged mining-core source |

Evidence (ignored local artifacts, not runtime data):

- `.agent-workspace/phase-wdm/TV2-NATIVE-COMBO-VFJq9f/RESULT.json`: PASS,
  16 cases; both real engines, actual source hashes and protected mining digests.
- Same directory `PREPARATION.json`: served build/JAR/core hashes, source inventory,
  Java/Node versions. Runner verifies source inventory remains unchanged.
- `client-personal-list-1440.png`, `client-personal-list-390.png`,
  `client-personal-detail.png`: visual evidence; synthetic catalog intentionally
  has no image files, so placeholder rendering is expected.
- `.agent-workspace/client-combo-20261110-190042/backend-gate.log`: 14 suites,
  96 tests, no failure/error/skip; includes isolated MySQL and application startup.
- `frontend-full-rerun.log`: 81 suites/1118 tests pass, exit 0. Two pre-existing
  opt-in live tests (`auth.live` and `health.live`) did not run: NOT VERIFIED by
  that command, not counted as successful tests. Native E2E separately uses real
  Auth/HTTP/MySQL and exits 0.
- `gate-build.log`, `package-gate.log`, `e2e-final.log`: successful final frontend
  build, isolated Maven package (skipTests is preparation only), E2E exit 0.

Earlier failed attempts are retained rather than reclassified as success: legacy
assertions were updated for authorized behavior, API/route regressions corrected,
and E2E selectors fixed before the final successful runs.

## Limits and local operation

Owner-database G502/Bose/customer_006 behavior is NOT VERIFIED by isolated tests.
Rebuild/restart the development backend and refresh the client before manually
approving/publishing a batch; existing running JARs do not acquire source edits.
No password reset, seed reload or schema migration is needed for this change.
Catalog optional fields remain absent when the database contains no value.

History matching reads a buyer's complete completed-order history and scans
eligible combos in pages before K. Large-catalog throughput/latency is NOT
VERIFIED; no performance/scalability claim follows from these correctness tests.
Edge at 1440px/390px is verified, not every browser or accessibility criterion.
Mining R6 and exact-real numeric equivalence remain unresolved and unchanged.

The current task's private `package/` contains generated temporary Maven POMs
pointing to repository sources solely to package an isolated JAR while the owner's
default JAR was locked. It is not a production build configuration change.
Intermediate task build folders were retained after automatic deletion review
returned `blocked by policy`; no pre-existing files were deleted or restored.

## Customer Top-K configuration — 2026-11-10

`recommendationK` is an integer from 1 through 20 (default 5) on the Admin
generation preview command. It is independent of presentation `k` and anchor.
The signed context freezes it; writer evidence and `COMBO_BATCH.top_k` retain
the chosen value. All eligible groups are persisted, including groups beyond K.
Changing customer K after saving requires a new batch identity. Old signed
contexts retain their original cap (5 for selected-product/category policies).

Public product recommendations omit the optional HTTP `k` and use the active
batch cap. An explicit request can reduce, never exceed, that cap. Public results
contain only same-order bundles; personal results contain history-matched
sequential/mixed recommendations. Each channel independently applies batch K
after its eligibility, product/history and self-suggestion filters. Ranking
remains support descending, pattern weight descending, then stable canonical/ID
ties. Fewer eligible recommendations can yield fewer than K.

Product detail initially shows two cards; “Xem thêm gợi ý” exposes the remaining
returned cards and “Thu gọn” restores the compact view. Product-list personal
recommendations remain fully accessible. Latest is a separate publication-time
feed of at most five same-order bundles. This supersedes the permanent two-card
limit recorded in historical FIX-AC04 below. No cart, order, mining threshold,
algorithm or review/publication policy changes are part of this feature.

## Compact layout correction — 2026-11-10

Owner accepted FIX-AC01..10 after reviewing the actual client screenshot.
Read-only development diagnosis: G502/Bose combo
`combo-90adb2bacbf49672f6aaa32b224c8a23` is APPROVED, batch
`batch-b29983f138154171b9b015d0f4d1f21d` is DRAFT, ACTIVE batch count is zero.
The running public latest endpoint returns an empty array. RUN is SUCCEEDED;
both products/categories are ACTIVE and writer evidence exists. This establishes
the missing publication state; it does not independently prove all evidence
content will pass the transactional publication authority. No development POST,
publication, catalog update, password change or history insertion was performed.

Admin now explains DRAFT versus public visibility and displays safe backend error
codes/request IDs on publication failure. Only an explicit Admin action publishes.
No backend production Java or mining-core changes were needed in this correction.

| Criterion | Result | Evidence |
|---|---|---|
| FIX-AC01 Diagnose actual G502/Bose visibility | PASS | Read-only local DB + actual HTTP; facts above and tutorial |
| FIX-AC02 Publish then read via API/client | PASS (isolated) | Actual native generation/review/publication E2E; owner publication remains NOT VERIFIED |
| FIX-AC03 Hide successful empty sections | PASS | Component tests; archive batch and real client reload in E2E |
| FIX-AC04 At most two cards inside right column | PASS | DOM containment, bounding boxes, dedup/priority tests, desktop/mobile screenshots |
| FIX-AC05 Product rows, checkboxes, exact total | PASS | Component arithmetic/selection tests and browser interaction |
| FIX-AC06 Constituent sidebar cards | PASS | Names/prices/image components and real three-item sidebar E2E |
| FIX-AC07 Color accents, responsive and errors | PASS (Edge scope) | Computed CSS color assertions, visual review, 1440/390px, retry/held-response tests |
| FIX-AC08 Three kinds preserve steps | PASS | Actual primary patterns: same-order steps [1,1,1], sequential [1,2,3], mixed [1,1,2] |
| FIX-AC09 Reproducible tutorial | PASS (synthetic pipeline) | Versioned 9-line CSV, actual examples in RESULT.json, PowerShell syntax check; local upload run NOT VERIFIED |
| FIX-AC10 Focused regression/build/scope | PASS | 97 focused frontend tests; final build; node syntax/diff checks; unchanged mining-core |

Final correction evidence:

- `.agent-workspace/phase-wdm/TV2-NATIVE-COMBO-FhOjyR/RESULT.json`: PASS,
  **17/17** cases; `threeItemExamples` contains actual run/batch/combo IDs, source
  CSV SHA-256, canonical patterns and product/step membership for all three kinds.
- `PREPARATION.json` in the same directory binds the served build, JAR and exact
  packaged/direct core hashes. Source inventory remained unchanged during the gate.
- `three-item-detail-1440.png`, `three-item-detail-390.png`, `three-item-list.png`,
  `client-detail-right-1440.png`: reviewed visual evidence. Synthetic catalog has
  no image assets; placeholders in these screenshots are intentional, not evidence
  of a failed development product-image request.
- `.agent-workspace/combo-layout-20261110-195039/focused-final.log`: seven suites,
  **97** tests pass, exit 0. No complete frontend-suite rerun was required for this
  scoped correction; the earlier 1118-test run remains earlier evidence.
- `final-build.log`: final build PASS with existing repository warnings;
  `e2e-final.log`: native gate exit 0, including computed color accents.
- Earlier attempts are retained, including correction of a test that picked its
  rejection candidate by random combo-ID order and could reject the sole
  sequential fixture. The final gate selects semantic cases explicitly.
- Backend fixture `backend/src/test/resources/wdm/csv-v1/combo-three-items.csv`:
  3 synthetic customers, 6 exact orders/itemsets, 9 lines/occurrences, 3 products;
  uploaded/validated/converted/mined/generated through real services, not seeded
  PATTERN/COMBO records.

The runner's execution-disabled restart call now passes overrides in its actual
third argument, so that case really starts with execution disabled. A separate
explicit execution-enabled restart runs the three-product tutorial fixture.

See [the three-product guide](combo-three-items-guide.md) for the local publication
step, ready-to-generate catalog-specific CSV and explicit limits on personal
history. The generated local CSV remains a demo artifact; no claim of successful
upload/mining against the owner's database is made. Existing user modifications,
pre-existing deletions and previous evidence were preserved; task build/log/demo
artifacts remain under the unique ignored correction directory.
