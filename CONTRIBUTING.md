# Contributing

`cloud-itonami-isic-4761` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real customer, employee, supplier or
  return/refund-dispute-incident data.
- Keep sales-record logging, staffing-operation scheduling, supply-order
  coordination and inventory-concern flagging behind the
  BookstoreRetailGovernor.
- Treat bookstore-operations workflows as high-risk: add tests for
  store/vendor verification, effect discipline, scope exclusion,
  escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "refund", "return", "title", "inventory") -- phrase it as the
  finalization/execution ACTION (e.g. "issue the refund", "ban the title
  from the store"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-inventory-concern` happy path -- see
  `bookstoreops.governor/scope-excluded-terms`'s docstring.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
