# cloud-itonami-isic-4761

Open Business Blueprint for **ISIC Rev.5 4761**: retail sale of books,
newspapers and stationary in specialized stores -- bookstore/newsstand/
stationery storefronts selling books, periodicals, newspapers, and
writing/office/school supplies (distinct from ISIC 4649-family wholesale
book/periodical distribution, which this actor never performs).

This repository publishes a bookstore/newsstand/stationery-retail
operations-COORDINATION actor -- sales/inventory/return transaction
logging, floor-staff scheduling, supply-order coordination with
registered publisher/distributor vendors, and inventory-concern flagging
-- as an OSS business that any qualified operator can fork, deploy, run,
improve and sell, so an independent bookstore never surrenders its
operations data to a closed back-office SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **BookstoreRetailAdvisor ⊣
BookstoreRetailGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:bookstore-retail-governor`, is a
distinct, independent build (no naming-collision precedent question --
distinct from sibling 47xx actors' own governor keywords, e.g. ISIC
4719's `:merchandise-retail-governor` and ISIC 4751's
`:textile-retail-governor`).

> **Why an actor layer at all?** An LLM is great at drafting a sales-
> record summary, a staffing proposal, or a supply-order request -- but
> it has no license to actually issue a refund, void a sale, or
> chargeback a vendor to finalize a return, no way to independently
> confirm a store or a supply-order vendor is actually a
> registered/verified counterparty, no authority to decide which titles
> a store may or may not carry, and no notion of when a "flag this
> concern" op quietly turns into a claim to have already resolved it.
> Letting it act directly invites an unverified store's data entering the
> ledger, an unverified vendor receiving a book/stationery order, or --
> worst of all -- a fabricated claim to have already refunded a customer,
> revoked a vendor's registration, or banned a title from sale over a
> disputed shipment, exposing the shop to real liability and the store's
> customers to unaccountable censorship. This project seals the
> BookstoreRetailAdvisor into a single node and wraps it with an
> independent **BookstoreRetailGovernor**, a human **approval workflow**,
> and an immutable **audit ledger**.

## Scope: coordination only, not censorship or returns authority

This actor is **operations coordination only**. It never performs or
authorizes:

- setting or overriding a shelf/unit price
- directly finalizing a return/refund (issuing a refund or replacement,
  voiding a sale, charging back a vendor, revoking or terminating a
  vendor's registration/contract)
- directly finalizing a content-inclusion/exclusion editorial decision
  (banning, pulling, or prohibiting a title from sale, or otherwise
  deciding which titles the store may or may not carry)

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging an inventory
concern for a human to triage is exactly this actor's job --
`:flag-inventory-concern` is never excluded by this check, only
FINALIZING/resolving/actuating on that concern (or on a title's
inclusion/exclusion) is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`bookstoreops.governor`'s `effect-not-propose-violations` HARD check and
`bookstoreops.phase`'s phase table, which never puts
`:flag-inventory-concern` in any phase's `:auto` set). A human store
operator/bookstore-inventory coordinator is always the one who actually
acts on a flagged concern or confirms a high-cost supply order.

## The core contract

```
store/vendor registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ BookstoreRetail-      │ ─────────────▶ │ BookstoreRetailGovernor     │  (independent system)
   │ Advisor (sealed)      │  + citations    │ store-unverified ·          │
   └───────────────────────┘                 │ vendor-unverified ·         │
          │                 commit ◀┼ effect-not-propose ·               │
          │                         │ scope-excluded (return/refund       │
    record + ledger        escalate ┼ finalization, content-inclusion/    │
          │              (ALWAYS for│ exclusion editorial finalization) · │
          │       :flag-inventory-  │ op-not-allowed                      │
          │       concern/high-cost │                                      │
          │       supply-order)     └────────────────────────────┘
          ▼
      human approval
```

**The BookstoreRetailAdvisor never commits a proposal the
BookstoreRetailGovernor would reject, and an inventory-concern flag or a
high-cost supply order never commits without a human sign-off.** Hard
violations (an unregistered/unverified store; an unregistered/unverified
supply-order vendor; a non-`:propose` effect; content touching
return/refund finalization or content-inclusion/exclusion editorial
finalization; an op outside the closed allowlist) force **hold** and
*cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: shelving, back-stock
dispensing, restocking, point-of-sale handling) under human/robot floor
operations gated by store policy. This actor itself does not dispatch
robot/hardware actions -- it is strictly the operations-coordination
layer (sales-record logging, staffing scheduling, supply-order
coordination, inventory-concern flagging) any physical-dispatch layer
could eventually feed proposals into, always gated the same way by the
independent BookstoreRetailGovernor.

## Features

- **Closed proposal-op allowlist**: `log-sales-record`,
  `schedule-staffing-operation`, `coordinate-supply-order`,
  `flag-inventory-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Store unverified** -- the target store's business registration
     must exist AND be independently registered/verified in the store.
  2. **Vendor unverified** -- for `:coordinate-supply-order` only, the
     named vendor (publisher/distributor) must exist AND be
     independently registered/verified -- a supply-chain
     counterparty-verification gate shared with sibling 47xx retail
     actors.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing a return/refund (refund/
     replacement issuance, sale voiding, vendor chargeback, vendor
     registration/contract revocation), directly finalizing a
     content-inclusion/exclusion editorial decision (banning, pulling, or
     prohibiting a title from sale), and an op outside the closed
     allowlist are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-inventory-concern` -- ALWAYS escalates, regardless of
    confidence or phase. A "flag a concern" op is never auto-commit
    eligible and never finalizes a return/refund or a content decision
    itself -- it only surfaces the concern for a human.
  - `:coordinate-supply-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: sales-record logging only (approval-gated)
  - Phase 2: + staffing-operation scheduling, supply-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (inventory concerns and high-cost supply orders always escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/bookstoreops/governor_test.clj` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/bookstoreops/advisor_test.clj` -- advisor proposal shape and
  consistency
- `test/bookstoreops/phase_test.clj` -- rollout phase logic
- `test/bookstoreops/governor_contract_test.clj` -- full graph
  integration, audit trail
- `test/bookstoreops/store_contract_test.clj` -- Store protocol and
  MemStore implementation

### Modules

- `bookstoreops.store` -- SSoT (MemStore, String-keyed store/vendor
  directories, append-only ledger)
- `bookstoreops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `bookstoreops.governor` -- independent compliance layer
- `bookstoreops.phase` -- staged rollout (0→3)
- `bookstoreops.operation` -- langgraph-clj StateGraph
- `bookstoreops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`4761`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Sales/inventory/return transaction logging (`:log-sales-record`) | Real POS/inventory-system integration |
| Floor-staff scheduling coordination (`:schedule-staffing-operation`) | Direct staff time-clock/payroll integration |
| Book/newspaper/stationery supply-order coordination with a registered, verified vendor, HARD-gated on vendor verification and a double-actuation-free single-proposal shape (`:coordinate-supply-order`) | Real supplier-ordering-system integration |
| Inventory-concern flagging (damaged stock, mis-shipment, packing-slip shortage), ALWAYS human-gated (`:flag-inventory-concern`) | Directly finalizing any return/refund, or any content-inclusion/exclusion editorial decision -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Daily reconciliation/cash-up -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a
return-authorization-intake or a shrinkage-observation check) as its own
governed op with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records before
any real-world act" pattern this repo's flagship checks already
establish.

## Maturity

`:implemented` -- `BookstoreRetailAdvisor` + `BookstoreRetailGovernor`
run as real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor and its own
supply-chain vendor-verification check.

## License

Code and implementation templates are AGPL-3.0-or-later.
