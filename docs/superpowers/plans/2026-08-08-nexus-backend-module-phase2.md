# Nexus Backend Module Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement every remaining bloxbean `BackendService` method the nexus-java-client SDK can back — account rewards/addresses/transactions, asset addresses, address getAllTransactions, tx redeemers — on the existing Phase-1 adapters.

**Architecture:** Add methods to the existing `Nexus{Account,Asset,Address,Transaction}Service` adapters. Same pattern: SDK sub-service call → `NexusResultMapper` → model mapping; SDK `ApiException` → bloxbean `ApiException`. New shared `NexusPagination.subList` for in-memory pagination of `(count,page)` variants. No `NexusBackendService` change.

**Tech Stack:** Java 17, Gradle, nexus-java-client 1.1.0 (mavenLocal), JUnit 5 + Mockito + AssertJ.

## Global Constraints

- Package `com.bloxbean.cardano.client.backend.nexus`. Java 17.
- Build (gradle needs JDK 21): `export JAVA_HOME="/Users/dudiedri/Library/Java/JavaVirtualMachines/corretto-21.0.11/Contents/Home"` then `./gradlew :backend-modules:nexus:test --console=plain [--tests '*Xxx*']`. Ignore Unsafe/deprecation/JaCoCo WARNINGs; check `BUILD SUCCESSFUL`/`Tests run`/`FAILED`.
- SDK source: `/private/tmp/.../scratchpad/nexus-java-client/src/main/java/adlabs/nexus/client/backend/api/`.
- Every new method: `NexusResultMapper.map(sdkCall, mapper)`; catch SDK `adlabs...base.exception.ApiException` → throw bloxbean `com.bloxbean.cardano.client.api.exception.ApiException(msg, cause)`. Add an exception-rethrow test for EACH new public method (Phase-1 reviews repeatedly flagged this gap).
- In-memory pagination via `NexusPagination.subList(List<T> full, int count, int page)`: `page` 1-based; `count` = page size; page < 1 or beyond range → empty list. (koios `KoiosUtxoService.getSubListByPage(list, pageNumber, pageSize)` is the reference but is AddressUtxo-typed + in the koios module — do NOT depend on it; add a generic nexus-local helper.)
- Order params ignored where the SDK has no order, EXCEPT apply a client-side reverse for `OrderEnum.desc` on the address/account transaction lists (cheap). Document each order-ignore.
- Resolved mappings (verified — use these):
  - `Purpose{SPEND,MINT,CERT,REWARD}` → bloxbean `RedeemerTag` via `RedeemerTag.convert(purpose.name())` (case-insensitive; returns null on no match).
  - `ExecutionUnit{Integer mem, Long steps}` → `unitMem = mem==null?null:mem.toString()`, `unitSteps = steps==null?null:steps.toString()`.
  - `Datum{String hash, JsonNode value}` → redeemer `datumHash` = datum.hash.
  - `Pagination.hasNext` (Boolean) terminates the address-history page loop.

---

## File structure

**Create:** `NexusPagination.java` + `NexusPaginationTest.java`.
**Modify:** `NexusAccountService`, `NexusAssetService`, `NexusAddressService`, `NexusTransactionService` (+ their test files).

---

### Task 1: NexusPagination helper

**Files:** Create `NexusPagination.java`; test `NexusPaginationTest.java`.

**Interfaces:** Produces `static <T> List<T> NexusPagination.subList(List<T> full, int count, int page)` — 1-based page; empty for null/empty input, page<1, or page beyond range.

- [ ] **Step 1: Write the failing test**

```java
package com.bloxbean.cardano.client.backend.nexus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NexusPaginationTest {
    private final List<Integer> ten = List.of(1,2,3,4,5,6,7,8,9,10);
    @Test void page1_size3() { assertThat(NexusPagination.subList(ten,3,1)).containsExactly(1,2,3); }
    @Test void page2_size3() { assertThat(NexusPagination.subList(ten,3,2)).containsExactly(4,5,6); }
    @Test void lastPartialPage() { assertThat(NexusPagination.subList(ten,3,4)).containsExactly(10); }
    @Test void pageBeyondRange_empty() { assertThat(NexusPagination.subList(ten,3,5)).isEmpty(); }
    @Test void pageLessThan1_empty() { assertThat(NexusPagination.subList(ten,3,0)).isEmpty(); }
    @Test void nullInput_empty() { assertThat(NexusPagination.subList(null,3,1)).isEmpty(); }
}
```

- [ ] **Step 2: Run to verify fail** — `./gradlew :backend-modules:nexus:test --tests '*Pagination*'` → FAIL.

- [ ] **Step 3: Implement**

```java
package com.bloxbean.cardano.client.backend.nexus;
import java.util.Collections;
import java.util.List;

public final class NexusPagination {
    private NexusPagination() {}
    public static <T> List<T> subList(List<T> full, int count, int page) {
        if (full == null || full.isEmpty() || page < 1 || count < 1) return Collections.emptyList();
        int from = (page - 1) * count;
        if (from >= full.size()) return Collections.emptyList();
        int to = Math.min(from + count, full.size());
        return full.subList(from, to);
    }
}
```

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): in-memory pagination helper`.

---

### Task 2: NexusAccountService — rewards, addresses, transactions

**Files:** Modify `NexusAccountService.java`; `NexusAccountServiceTest.java`.

**Interfaces:** Implements (replacing the Phase-1 Unsupported throws) `getAccountRewardsHistory`, `getAllAccountAddresses`, `getAccountAddresses`, `getAccountTransactions`, `getAllAccountTransactions`. Leaves `getAccountHistory`, `getAccountAssets`, `getAllAccountAssets` throwing Unsupported.

- [ ] **Step 1: Read the SDK + bloxbean models** — SDK `account/model/{AccountRewardsHistory,AccountAddress,AccountTransaction}.java`; bloxbean `backend/model/{AccountRewardsHistory,AccountAddress,AddressTransactionContent}.java`. SDK methods: `getAccountRewards(network,stake)`, `getAccountAddresses(network,stake)`, `getAccountTransactions(network,stake,int fromBlockHeight)`.

- [ ] **Step 2: Write failing tests** (mock SDK account service):
  - `getAccountRewardsHistory(stake, count, page, order)`: SDK `getAccountRewards` returns 4 rows; assert mapped `AccountRewardsHistory{epoch,amount,poolId,type}` + pagination (page 1 size 2 → first 2) + order ignored.
  - `getAllAccountAddresses(stake)`: SDK `getAccountAddresses` → assert mapped `AccountAddress{address}` list, all rows.
  - `getAccountAddresses(stake,count,page)`: same source, in-memory paginated.
  - `getAccountTransactions(stake,count,page,order,fromBH,toBH)`: SDK `getAccountTransactions(network,stake,fromBH)` returns rows; assert `AddressTransactionContent{txHash, txIndex=0, blockHeight, blockTime}`, toBH client-filter, pagination, desc reverse.
  - `getAllAccountTransactions(stake,order,fromBH,toBH)`: full list, toBH filter, desc reverse.
  - Exception-rethrow test for at least `getAccountRewardsHistory` + `getAccountTransactions`.
  - Still-Unsupported: `getAccountHistory` + `getAccountAssets` → assert `UnsupportedOperationException`.

- [ ] **Step 3: Run to verify fail.**

- [ ] **Step 4: Implement** — each method: guard `network`, call SDK, `NexusResultMapper.map(..., list -> map+filter+paginate)`, catch SDK ApiException → rethrow. `fromBlockHeight` default: pass `fromBH == null ? 0 : fromBH`. `toBH` filter: `blockHeight <= toBH` when toBH != null. Order: reverse the mapped list for `OrderEnum.desc`. Paginate the `(count,page)` variants via `NexusPagination.subList`. Map `AccountTransaction.txIndex`→0 (SDK has none), primitive-long null-guards. Keep `getAccountHistory`/`getAccountAssets`/`getAllAccountAssets` throwing Unsupported.

- [ ] **Step 5: Run to verify pass.** **Step 6: Commit** `feat(nexus): account rewards + addresses + transactions`.

---

### Task 3: NexusAssetService — asset addresses

**Files:** Modify `NexusAssetService.java`; `NexusAssetServiceTest.java`.

**Interfaces:** Implements `getAllAssetAddresses`, `getAssetAddresses`. Leaves `getAllPolicyAssets`, `getPolicyAssets`, `getTransactions` Unsupported.

- [ ] **Step 1: Write failing tests** — SDK `getNftAddress(network, policyId, assetName)` → `List<PaymentAddress>`:
  - `getAllAssetAddresses(unit)`: split unit (policy 56 + name), assert SDK called positionally, assert mapped `AssetAddress{address=paymentAddress, quantity=null}` (quantity null — documented; SDK has no per-address quantity).
  - `getAssetAddresses(unit,count,page)`: in-memory paginated.
  - exception-rethrow test; `getPolicyAssets` + asset `getTransactions` → Unsupported.

- [ ] **Step 2: Run to verify fail.**

- [ ] **Step 3: Implement** — reuse the Phase-1 unit-split helper (policyId first 56 chars, assetName remainder, empty when ≤56). Map `PaymentAddress.paymentAddress`→`AssetAddress.address`; `quantity` = null (1-line comment: SDK returns holder addresses, no per-address quantity). Paginate the count/page variant. Keep policy-asset + asset-tx methods Unsupported.

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): asset addresses`.

---

### Task 4: NexusAddressService — getAllTransactions (history page-loop)

**Files:** Modify `NexusAddressService.java`; `NexusAddressServiceTest.java`.

**Interfaces:** Implements `getAllTransactions(address, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight)`. Leaves `getAddressDetails` Unsupported.

- [ ] **Step 1: Read** SDK `getAddressTransactionHistory(network,address,int page,int pageSize)` → `TransactionHistoryResponse{List<TransactionHistoryItem> transactions, Pagination pagination}`; `TransactionHistoryItem{txHash, txTimestamp, blockHeight, ...}`; `Pagination{hasNext, ...}`. bloxbean `AddressTransactionContent{txHash, txIndex, blockHeight, blockTime}`.

- [ ] **Step 2: Write failing tests** (mock SDK):
  - `getAllTransactions`: mock 2 pages of history (page 1 hasNext=true, page 2 hasNext=false); assert BOTH pages accumulated + mapped `AddressTransactionContent{txHash, txIndex=0, blockHeight, blockTime=txTimestamp}`; assert the loop stops when hasNext=false.
  - block-height filter: rows outside `[fromBH,toBH]` excluded.
  - desc order reverses.
  - exception-rethrow test.

- [ ] **Step 3: Run to verify fail.**

- [ ] **Step 4: Implement** — a private helper that pages the SDK history (start page 1, fixed pageSize e.g. 100) accumulating `transactions` while `pagination.hasNext == TRUE`, with a **max-page safety cap** (e.g. 1000 pages) to avoid an infinite loop on a misbehaving `hasNext`; map items → `AddressTransactionContent`; client-filter `blockHeight` in `[fromBH, toBH]` (null bounds = open); reverse for `OrderEnum.desc`. Wrap in `NexusResultMapper`-style success/error (note: this is multi-call — if any page returns an unsuccessful SDK Result, return that as a bloxbean error Result; on SDK ApiException, rethrow bloxbean ApiException).

- [ ] **Step 5: Run to verify pass.** **Step 6: Commit** `feat(nexus): address getAllTransactions via history page-loop`.

---

### Task 5: NexusTransactionService — getTransactionRedeemers

**Files:** Modify `NexusTransactionService.java`; `NexusTransactionServiceTest.java`.

**Interfaces:** Implements `getTransactionRedeemers(txHash)` (replacing the Phase-1 Unsupported throw).

- [ ] **Step 1: Read** SDK `transaction/model/{Transaction,TxPlutusContract,PlutusScriptInput,PlutusScriptRedeemer,ExecutionUnit,Datum,Purpose}.java`; bloxbean `backend/model/TxContentRedeemers.java` + `TxContentRedeemerPurpose`/`RedeemerTag`. SDK path: `getTransaction(network,txHash).getPlutusContracts()` → `List<TxPlutusContract>`; each `TxPlutusContract{scriptHash, input:PlutusScriptInput{redeemer:PlutusScriptRedeemer{purpose:Purpose, fee:String, unit:ExecutionUnit, datum:Datum}, datum:Datum}}`.

- [ ] **Step 2: Write failing tests** (mock SDK tx service):
  - `getTransactionRedeemers(txHash)`: SDK `getTransaction` returns a Transaction with 1 plutusContract (purpose SPEND, fee "1000", unit mem=500/steps=1000000L, datum hash "d1", scriptHash "s1"); assert bloxbean `TxContentRedeemers{txIndex=0, purpose=RedeemerTag.Spend, scriptHash="s1", fee="1000", unitMem="500", unitSteps="1000000", datumHash="d1"}`.
  - null-guard test: a plutusContract with null `input` (or null redeemer) → does not NPE; maps the available fields (scriptHash) and leaves redeemer-derived fields null.
  - empty plutusContracts → empty list success.
  - exception-rethrow test.

- [ ] **Step 3: Run to verify fail.**

- [ ] **Step 4: Implement** — `getTransactionRedeemers(txHash)`: `NexusResultMapper.map(api.getTransaction(network, txHash), tx -> toRedeemers(tx.getPlutusContracts()))`. `toRedeemers`: null/empty → empty list; else index each `TxPlutusContract c` (index i) → `TxContentRedeemers.builder()`:
  - `.txIndex(i)`
  - `.scriptHash(c.getScriptHash())`
  - from `r = c.getInput()==null?null:c.getInput().getRedeemer()`: `.purpose(r==null||r.getPurpose()==null?null:RedeemerTag.convert(r.getPurpose().name()))`, `.fee(r==null?null:r.getFee())`, `.unitMem(r==null||r.getUnit()==null||r.getUnit().getMem()==null?null:r.getUnit().getMem().toString())`, `.unitSteps(r==null||r.getUnit()==null||r.getUnit().getSteps()==null?null:r.getUnit().getSteps().toString())`, `.datumHash(r==null||r.getDatum()==null?null:r.getDatum().getHash())`
  - `.redeemerDataHash(null)` (no SDK source — document)
  - `.build()`.
  VERIFY the exact bloxbean `TxContentRedeemers` field setters/builder + `RedeemerTag` package before writing; adjust names if the model differs. Keep the exact-verified `RedeemerTag.convert(purpose.name())` mapping.

- [ ] **Step 5: Run to verify pass.** **Step 6: Commit** `feat(nexus): transaction redeemers from plutus contracts`.

---

### Task 6: Full module build + Unsupported audit

**Files:** none (verification + any fixups).

- [ ] **Step 1: Full module build** — `./gradlew :backend-modules:nexus:build --console=plain` → BUILD SUCCESSFUL, all tests green (Phase-1 + Phase-2). Report the total test count.
- [ ] **Step 2: Unsupported audit** — confirm the documented still-unsupported methods still throw `UnsupportedOperationException` (grep the 4 adapters for the remaining throws: `AccountService.getAccountHistory`/`getAccountAssets`/`getAllAccountAssets`, `AssetService.getAllPolicyAssets`/`getPolicyAssets`/`getTransactions`, `AddressService.getAddressDetails`); confirm none was accidentally left half-implemented. Report the list.
- [ ] **Step 3: Commit** any fixups (or note clean). Do NOT push (controller handles push after final review).

---

## Self-review

**Spec coverage:** pagination helper → Task 1. Account rewards/addresses/transactions → Task 2. Asset addresses → Task 3. Address getAllTransactions → Task 4. Tx redeemers → Task 5. Full build + Unsupported audit → Task 6. Testing (mapping + pagination slice + block-height filter + exception rethrow per method + still-Unsupported) → every task. Documented nulls (AssetAddress.quantity, txIndex, redeemerDataHash) → Tasks 2/3/5.

**Placeholder scan:** no TBD/TODO. Redeemer mapping is fully spelled out with the verified enum/field mappings + null-guards; the one "VERIFY the bloxbean TxContentRedeemers builder/field names" step is a concrete compile-guard, not a placeholder. Pagination + history-loop are concrete code.

**Type consistency:** `NexusPagination.subList(list, count, page)` signature consistent across Tasks 2/3. `RedeemerTag.convert(purpose.name())`, `ExecutionUnit` getters, `Pagination.hasNext` all as resolved in Global Constraints. Adapters modified are the Phase-1 classes; no interface signature changes (implementing existing bloxbean interface methods).
