(ns bookstoreops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave1 Lane A-no-demo): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`bookstoreops.operation` -> `bookstoreops.governor` ->
  `bookstoreops.store`) through a scenario adapted from this repo's own
  `bookstoreops.sim` demo driver (`clojure -M:run`, confirmed BEFORE
  writing this file to produce a sensible ledger against the real seeded
  store/vendor ids `store-1`..`store-3` / `vendor-1`..`vendor-2` -- they
  match `bookstoreops.store/demo-data` exactly, so it was safe to reuse
  rather than author from scratch), trimmed to a representative subset
  (clean phase-3 auto-commits, always-escalate high-cost supply-order +
  inventory-concern flags that a human approves, and several distinct
  HARD-hold reasons) and rendered deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verify by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [bookstoreops.advisor :as advisor]
            [bookstoreops.store :as store]
            [bookstoreops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "coord-1" :actor-role :bookstore-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "bookstore-inventory-coordinator-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: store-1 clears clean phase-3 auto-commits
  (`:log-sales-record`, `:schedule-staffing-operation`, low-cost
  `:coordinate-supply-order` naming verified vendor-1), then a high-cost
  supply order (ALWAYS escalates -- estimated-cost above
  `bookstoreops.governor/supply-cost-threshold`, approved) and an
  inventory-concern flag (ALWAYS escalates at any phase -- approved);
  store-99 HARD-holds on an unregistered store; store-3 HARD-holds on a
  registered-but-unverified store; a supply order naming unverified
  vendor-2 HARD-holds on `:vendor-unverified`; an advisor that claims
  `:effect :commit` HARD-holds on `:effect-not-propose`; a proposal that
  drifts into return/refund finalization HARD-holds on `:scope-excluded`.
  Every HARD hold never reaches a human. Returns the resulting store --
  every field read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; store-1: clean sales-record logging -- phase-3 auto-commit.
    (exec! actor "t1-sales" {:op :log-sales-record :store-id "store-1"
                              :patch {:units-sold 34 :returns 1 :stock-count-delta -35}})

    ;; store-1: clean staffing schedule -- phase-3 auto-commit.
    (exec! actor "t2-staff" {:op :schedule-staffing-operation :store-id "store-1"
                              :patch {:shift "weekend-register" :date "2026-07-20"
                                      :window "10:00-18:00"}})

    ;; store-1: low-cost supply order, verified vendor -- phase-3 auto-commit.
    (exec! actor "t3-supply-low" {:op :coordinate-supply-order :store-id "store-1"
                                   :patch {:item "front-list hardcover restock"
                                           :quantity 40 :estimated-cost 360.0
                                           :vendor-id "vendor-1"}})

    ;; store-1: HIGH-cost supply order -- ALWAYS escalates; human approves.
    (exec! actor "t4-supply-high" {:op :coordinate-supply-order :store-id "store-1"
                                    :patch {:item "back-to-school stationery bulk order"
                                            :quantity 600 :estimated-cost 2400.0
                                            :vendor-id "vendor-1"}})
    (approve! actor "t4-supply-high")

    ;; store-1: inventory concern -- ALWAYS escalates; human approves.
    (exec! actor "t5-concern" {:op :flag-inventory-concern :store-id "store-1"
                                :patch {:concern "carton #4471 arrived water-damaged and the packing slip claims 40 units but only 33 were received"
                                        :confidence 0.9}})
    (approve! actor "t5-concern")

    ;; store-99: unregistered store -> HARD hold (:store-unverified).
    (exec! actor "t6-unreg" {:op :log-sales-record :store-id "store-99"
                              :patch {:units-sold 0}})

    ;; store-3: registered but unverified -> HARD hold (:store-unverified).
    (exec! actor "t7-unverified" {:op :log-sales-record :store-id "store-3"
                                   :patch {:units-sold 10}})

    ;; store-1 + vendor-2 unverified -> HARD hold (:vendor-unverified).
    (exec! actor "t8-vendor" {:op :coordinate-supply-order :store-id "store-1"
                               :patch {:item "imported specialty notebooks"
                                       :quantity 25 :estimated-cost 300.0
                                       :vendor-id "vendor-2"}})

    ;; advisor claims direct actuation (:effect :commit) -> HARD hold.
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "t9-effect" {:op :schedule-staffing-operation :store-id "store-1"
                                        :patch {:shift "weekday-register" :date "2026-07-22"}}))

    ;; advisor drifts into return/refund-finalization scope -> HARD hold.
    (exec! actor "t10-scope" {:op :log-sales-record :store-id "store-1"
                               :out-of-scope? true
                               :patch {}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger store-id]
  (last (filter #(= (:store-id %) store-id) ledger)))

(defn- status-cell [ledger store-id]
  (let [f (last-fact-for ledger store-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (or (-> f :violations first :rule)
                     (first (:basis f)))]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- reg-cell [{:keys [registered? verified?]}]
  (cond
    (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
    registered? "<span class=\"warn\">registered, not verified</span>"
    :else "<span class=\"critical\">unregistered</span>"))

(defn- store-row [ledger {:keys [store-id name] :as s}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc store-id) (esc name) (reg-cell s) (status-cell ledger store-id)))

(defn- vendor-row [{:keys [vendor-id name] :as v}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc vendor-id) (esc name) (reg-cell v)))

(defn- ledger-row [{:keys [t op store-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc store-id)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                   (some-> disposition name) ""))))

(defn- coord-row [{:keys [op store-id value]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name (or op :n-a))) (esc store-id)
          (esc (pr-str (or value {})))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops`, `bookstoreops.governor`/`bookstoreops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-sales-record</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high-confidence · sales/inventory/return transaction logging only</span></td></tr>"
   "        <tr><td><code>:schedule-staffing-operation</code></td><td><span class=\"ok\">phase-3 auto-commit when clean &amp; high-confidence · floor-staff roster proposal</span></td></tr>"
   "        <tr><td><code>:coordinate-supply-order</code></td><td><span class=\"warn\">phase-3 auto when clean &amp; low-cost · ALWAYS human approval when estimated-cost &gt; supply-cost-threshold · vendor must be independently verified</span></td></tr>"
   "        <tr><td><code>:flag-inventory-concern</code></td><td><span class=\"warn\">ALWAYS human approval · never auto at any phase · surfaces damaged-stock/mis-shipment concerns only</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        stores (store/all-store-records db)
        vendors (store/all-vendor-records db)
        coords (store/coordination-log db)
        store-rows (str/join "\n" (map (partial store-row ledger) stores))
        vendor-rows (str/join "\n" (map vendor-row vendors))
        coord-rows (str/join "\n" (map coord-row coords))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-4761 &middot; retail sale of books, newspapers and stationary</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Retail sale of books, newspapers and stationary (ISIC 4761) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · inventory concerns &amp; high-cost supply orders always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Stores</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>bookstoreops.store</code> via <code>bookstoreops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly. A store must be independently registered &amp; verified before any proposal targeting it may commit or escalate.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Store</th><th>Name</th><th>Registration</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     store-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Publisher / distributor vendors</h2>\n"
     "    <p class=\"muted\"><code>:coordinate-supply-order</code> must name a vendor that is independently registered &amp; verified — ground truth from the store directory, never proposal self-report.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Vendor</th><th>Name</th><th>Registration</th></tr></thead>\n"
     "      <tbody>\n"
     vendor-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Committed coordination log (this run)</h2>\n"
     "    <p class=\"muted\">Proposals that passed the BookstoreRetailGovernor and (when required) human approval — coordination only, never a refund finalization or content-inclusion decision.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Store</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     coord-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Bookstore Retail Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Store and vendor registration are re-derived from the store directory; <code>:effect</code> must be <code>:propose</code>; return/refund finalization and content-inclusion/exclusion editorial finalization are permanently out of scope.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Store</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "coordination records )")))
