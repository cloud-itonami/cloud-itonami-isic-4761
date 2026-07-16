(ns bookstoreops.governor
  "BookstoreRetailGovernor -- the independent compliance layer that earns
  the BookstoreRetailAdvisor the right to commit. The advisor has no
  notion of whether a store is actually registered and license-verified,
  whether a named supply-order vendor (publisher/distributor) is itself a
  registered/verified counterparty, whether its own proposed `:effect`
  secretly claims a direct actuation instead of a mere proposal, or
  whether it has silently drifted into a permanently out-of-scope
  decision area, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COORDINATION ONLY
  (sales/inventory/return transaction logging, floor-staff scheduling,
  supply-order coordination with publisher/distributor vendors,
  inventory-concern flagging). It is NOT a content-censorship authority
  and NOT a returns-authorization authority. It NEVER performs or
  authorizes:
    - setting or overriding a shelf/unit price
    - directly finalizing a return/refund (issuing a refund or
      replacement, voiding a sale, charging back a vendor, revoking or
      terminating a vendor's registration/contract)
    - directly finalizing a content-inclusion/exclusion editorial
      decision (banning, pulling, or prohibiting a title from sale, or
      otherwise deciding which titles the store may or may not carry) --
      that decision is permanently out of scope for a retail-operations
      coordinator, not merely un-implemented

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Store unverified           -- the target store record must exist
                                     AND be independently confirmed
                                     `:registered?`/`:verified?` in the
                                     store before ANY proposal for it may
                                     commit or even escalate. Never trusts
                                     a proposal's own claim about the
                                     store -- re-derived from the store's
                                     own record, the same 'ground truth,
                                     not self-report' discipline every
                                     sibling actor's governor uses.
    2. Vendor unverified          -- for `:coordinate-supply-order` ONLY,
                                     the proposal's own drafted `:value`
                                     must name a `:vendor-id` that
                                     resolves to an independently
                                     `:registered?`/`:verified?` vendor
                                     (publisher/distributor) record. A
                                     missing vendor-id, or one that
                                     resolves to an unregistered or
                                     unverified vendor, is a HARD block.
    3. Effect not :propose        -- every proposal's `:effect` MUST be
                                     `:propose`. Any other effect value
                                     is, by construction, a claim to
                                     directly actuate/commit outside
                                     governance -- HARD block, not merely
                                     low-confidence.
    4. Scope exclusion            -- ANY proposal (regardless of op)
                                     whose op, summary, rationale, cites
                                     or draft value touches directly
                                     finalizing a return/refund (issuing a
                                     refund or replacement, voiding a
                                     sale, charging back a vendor,
                                     revoking or terminating a vendor's
                                     registration/contract) OR directly
                                     finalizing a content-inclusion/
                                     exclusion editorial decision (banning,
                                     pulling, or prohibiting a title from
                                     sale) is a HARD, PERMANENT block --
                                     this actor's charter excludes both
                                     territories structurally, not as a
                                     rollout milestone. Evaluated
                                     UNCONDITIONALLY on every proposal. An
                                     op outside the closed four-op
                                     allowlist is the SAME failure mode
                                     (an advisor proposing something it
                                     was never authorized to propose) and
                                     is folded into this same check.
                                     `:flag-inventory-concern` itself is
                                     never excluded by this check --
                                     surfacing a damaged-stock/mis-
                                     shipment concern for a human is
                                     exactly this actor's job; only
                                     FINALIZING/resolving/actuating on
                                     that concern (or on a title's
                                     inclusion/exclusion) is excluded (see
                                     `scope-excluded-terms` below --
                                     phrased as the finalization/execution
                                     ACTION, never a bare noun like
                                     'refund', 'return', or 'inventory',
                                     so the default mock advisor's own
                                     `:flag-inventory-concern` rationale
                                     never self-trips this check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-inventory-concern` -- ALWAYS escalates to a human,
      regardless of confidence, regardless of how clean the proposal
      otherwise is. `bookstoreops.phase` independently agrees:
      `:flag-inventory-concern` is never a member of any phase's `:auto`
      set either -- two layers, not one.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      publisher/distributor order always needs a human sign-off, even
      when the governor and phase would otherwise allow auto-commit."
  (:require [clojure.string :as str]
            [bookstoreops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example single-store book/stationery procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  800.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`)."
  #{:log-sales-record :schedule-staffing-operation
    :coordinate-supply-order :flag-inventory-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-inventory-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing a
  return/refund (issuing a refund or replacement, voiding a sale,
  charging back a vendor, revoking or terminating a vendor's
  registration/contract) OR directly finalizing a content-inclusion/
  exclusion editorial decision (banning, pulling, or prohibiting a title
  from sale) -- rather than merely logging a transaction or flagging a
  concern for a human. Scanned across the proposal's op/summary/
  rationale/cites/value, never trusting the advisor's own framing of its
  intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'issue the refund', 'ban the title from the store'), never
  a bare noun like 'refund', 'return', 'title' or 'inventory' -- a bare
  noun would accidentally match inside this actor's own legitimate
  `:flag-inventory-concern` default proposal text (whose whole job is to
  talk about damaged-stock/mis-shipment inventory concerns, and whose own
  printed `:op` keyword literally contains the substring
  'inventory-concern') and self-block the happy path. See
  `bookstoreops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["issue the refund" "issued the refund" "issuing the refund"
   "authorize the refund" "authorized the refund" "authorizing the refund"
   "approve the refund" "approved the refund" "approving the refund"
   "issue a replacement" "issued a replacement" "issuing a replacement"
   "authorize the replacement" "authorized the replacement" "authorizing the replacement"
   "approve the replacement" "approved the replacement" "approving the replacement"
   "void the sale" "voided the sale" "voiding the sale"
   "finalize the return" "finalized the return" "finalizing the return"
   "process the return" "processed the return" "processing the return"
   "issue a chargeback to" "issued a chargeback to" "issuing a chargeback to"
   "charge back the vendor" "charged back the vendor" "charging back the vendor"
   "revoke the vendor's registration" "revoked the vendor's registration" "revoking the vendor's registration"
   "terminate the vendor contract" "terminated the vendor contract" "terminating the vendor contract"
   "ban the title from the store" "banned the title from the store" "banning the title from the store"
   "remove the title from the catalog" "removed the title from the catalog" "removing the title from the catalog"
   "pull the title from the shelves" "pulled the title from the shelves" "pulling the title from the shelves"
   "prohibit the title from sale" "prohibited the title from sale" "prohibiting the title from sale"
   "finalize the content-inclusion decision" "finalized the content-inclusion decision" "finalizing the content-inclusion decision"
   "finalize the content-exclusion decision" "finalized the content-exclusion decision" "finalizing the content-exclusion decision"
   "返金を実行" "返金を実行した" "返金を承認した" "返金を承認して"
   "返品を確定した" "返品を確定して"
   "タイトルを発禁にした" "書籍を発禁にした" "取り扱いを禁止にした" "タイトルを店頭から排除した"
   "仕入先へのチャージバックを実行" "仕入先へのチャージバックを実行した"
   "仕入先の登録を取り消した" "仕入先契約を解除した"
   "販売を無効化した" "取引を無効にした"])

;; ----------------------------- checks -----------------------------

(defn- store-unverified-violations
  "The target store must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the proposal's
  own `:store-id` claim without a store lookup."
  [{:keys [store-id]} st]
  (let [s (store/store-record st store-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :store-unverified
        :detail (str store-id " は未登録または未検証の店舗 -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-supply-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` vendor record. A missing vendor-id, or one
  that resolves to an unregistered/unverified vendor, is a HARD block --
  never trust the proposal's own vendor claim without a store lookup, the
  SAME 'ground truth, not self-report' discipline as
  `store-unverified-violations`, reapplied to the supply-chain
  counterparty (publisher/distributor)."
  [proposal st]
  (when (= :coordinate-supply-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証の仕入先 -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing a return/refund (refund/
  replacement issuance, sale voiding, vendor chargeback, vendor
  registration/contract revocation) OR directly finalizing a content-
  inclusion/exclusion editorial decision (banning, pulling, or
  prohibiting a title from sale), regardless of confidence or how clean
  every other check is. Evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "返金/交換の実行・販売の無効化・仕入先チャージバック・登録取消などの返品確定行為、またはタイトルの発禁/排除など内容の掲載可否確定行為(content-inclusion/exclusion editorial finalization)に触れる提案は永久に禁止"}])))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost` above
  `supply-cost-threshold` -- always needs human sign-off (SOFT escalate,
  not a hard block: the order itself is in scope, only its size requires
  a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors a BookstoreRetailAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [store-id (or (:store-id proposal) (:store-id request))
        hard (into []
                   (concat (store-unverified-violations {:store-id store-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :store-id   (:store-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
