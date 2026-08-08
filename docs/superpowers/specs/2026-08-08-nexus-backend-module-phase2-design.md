# Nexus Backend Module — Phase 2 (Additional SDK-Backed Coverage) Design

Date: 2026-08-08
Status: Approved design — ready for implementation plan
Branch: `feat/nexus-backend-module-phase2`, stacked on `feat/nexus-backend-module` (Phase-1 PR Gero-Labs/cardano-client-lib#1).

## Why

Phase 1 shipped a tx-capable Nexus provider with Account/Asset/Pool primary-read-only and several
methods stubbed `UnsupportedOperationException`. Phase 2 implements **every remaining bloxbean
method the SDK can actually back**, tightening coverage toward parity. Methods with no SDK data
source stay documented `UnsupportedOperationException` — this is not literal-full coverage.

## Scope

**Newly implemented (SDK-backed):**
- `NexusAccountService`: `getAccountRewardsHistory`, `getAllAccountAddresses` + `getAccountAddresses`, `getAccountTransactions` + `getAllAccountTransactions`.
- `NexusAssetService`: `getAllAssetAddresses` + `getAssetAddresses`.
- `NexusAddressService`: `getAllTransactions`.
- `NexusTransactionService`: `getTransactionRedeemers`.

**Remains `UnsupportedOperationException` (no SDK source — documented gaps):**
`AccountService.getAccountHistory` (no stake-per-epoch SDK model), `getAccountAssets`/`getAllAccountAssets`
(no SDK account-assets service method); `AssetService.getPolicyAssets`/`getAllPolicyAssets` + asset
`getTransactions`; `AddressService.getAddressDetails`; `TransactionService.evaluateTx` (default);
`UtxoService.isUsedAddress` (default). Pool is already complete.

**Pagination:** where the SDK returns a full (non-paginated) list and bloxbean exposes a
`(count, page)` variant, slice client-side via a shared `NexusPagination.subList(list, count, page)`
helper (koios `getSubListByPage` pattern: `page` is 1-based; page < 1 → empty). `getAll*` variants
return the full mapped list.

Out of scope: SDK/server changes; live integration tests.

## Per-method mapping

### NexusAccountService (SDK `account.AccountService`)
| bloxbean method | SDK source | mapping / notes |
|---|---|---|
| `getAccountRewardsHistory(stake,count,page[,order])` | `getAccountRewards(network,stake)` → `List<AccountRewardsHistory>` | map `{epoch,amount,poolId,type}` (SDK extras `spendableEpoch`/`stakeAddress` dropped); in-memory paginate; order ignored (documented) |
| `getAccountHistory(...)` | — | **Unsupported** (no SDK stake-per-epoch model) |
| `getAllAccountAddresses(stake)` | `getAccountAddresses(network,stake)` → `List<AccountAddress>` | map `{address}` → bloxbean `AccountAddress{address}` |
| `getAccountAddresses(stake,count,page[,order])` | same | map + in-memory paginate |
| `getAllAccountAssets(...)` / `getAccountAssets(...)` | — | **Unsupported** (no SDK account-assets method) |
| `getAccountTransactions(stake,count,page,order,fromBH,toBH)` | `getAccountTransactions(network,stake,fromBlockHeight)` → `List<AccountTransaction>` | map `AccountTransaction{txHash,epochNo,blockHeight,blockTime}` → `AddressTransactionContent{txHash, txIndex=0 (SDK has none), blockHeight, blockTime}` (primitive-long null-guard); pass `fromBH` (default 0) to SDK; in-memory filter `toBH` + paginate; order ignored |
| `getAllAccountTransactions(stake,order,fromBH,toBH)` | same | full list, filter `toBH`, no pagination |

### NexusAssetService (SDK `asset.AssetService`)
| bloxbean method | SDK source | mapping / notes |
|---|---|---|
| `getAllAssetAddresses(asset)` | split `asset` → policy(56)+name; `getNftAddress(network,policy,name)` → `List<PaymentAddress>` | map `PaymentAddress{paymentAddress,stakeAddress}` → `AssetAddress{address=paymentAddress, quantity=null}`. **`quantity` is null** — the SDK endpoint returns holder addresses without per-address quantity; document. |
| `getAssetAddresses(asset,count,page[,order])` | same | map + in-memory paginate |
| `getAllPolicyAssets`/`getPolicyAssets`/`getTransactions(asset,...)` | — | **Unsupported** (no SDK policy-assets or asset-tx method) |

### NexusAddressService (SDK `address.AddressService`)
| bloxbean method | SDK source | mapping / notes |
|---|---|---|
| `getAllTransactions(address,order,fromBH,toBH)` | `getAddressTransactionHistory(network,address,page,pageSize)` → `TransactionHistoryResponse{transactions,pagination}` | **page-loop** the SDK history until `pagination.hasNext` is false (or a sane max-page cap — see open items), accumulating `TransactionHistoryItem` → `AddressTransactionContent{txHash, txIndex=0, blockHeight, blockTime=txTimestamp}`; filter block-height range `[fromBH,toBH]` client-side; apply order (asc default; reverse for desc). |

### NexusTransactionService (SDK `transaction.Transaction.plutusContracts`)
| bloxbean method | SDK source | mapping / notes |
|---|---|---|
| `getTransactionRedeemers(txHash)` | `getTransaction(network,txHash).getPlutusContracts()` → `List<TxPlutusContract>` | each `TxPlutusContract{scriptHash, input:PlutusScriptInput{redeemer:PlutusScriptRedeemer{purpose,fee,unit:ExecutionUnit,datum}}}` → bloxbean `TxContentRedeemers{txIndex, purpose:RedeemerTag, scriptHash, redeemerDataHash, datumHash, unitMem, unitSteps, fee}`. Map: `scriptHash`←TxPlutusContract.scriptHash; `purpose`←map SDK `Purpose{SPEND,MINT,CERT,REWARD}`→bloxbean `RedeemerTag` (verify enum values); `fee`←redeemer.fee; `unitMem`/`unitSteps`←redeemer.unit (`ExecutionUnit` — verify field names, e.g. mem/steps); `datumHash`←redeemer.datum (verify Datum shape → hash); `txIndex`←list index (or null if bloxbean means something else — verify); `redeemerDataHash`←null if no SDK source. Guard nulls at every level (input/redeemer/unit may be null). |

## Design details

- **`NexusPagination` helper** (new small util in the nexus package): `static <T> List<T> subList(List<T> full, int count, int page)` — koios `getSubListByPage` semantics (1-based page; out-of-range → empty). Reused by all in-memory-paginated methods. (Check whether cardano-client-lib already exposes a reusable paginator before adding one; if koios's is private, add the nexus-local helper.)
- **Order params** are ignored where the SDK has no order (documented per method), except `getAllTransactions`/account-tx where a simple client-side reverse for `OrderEnum.desc` is cheap — apply it there.
- All new methods: `NexusResultMapper` for Result conversion; SDK `ApiException` → bloxbean `ApiException`; consistent null-guards.
- No `NexusBackendService` change (adapters already wired in Phase 1; Phase 2 only adds methods to existing adapter classes).

## Testing (unit, mock SDK)

Per new method: (a) success mapping (assert field-by-field incl. the documented nulls: AssetAddress.quantity, txIndex); (b) in-memory pagination slice (assert page 1 vs page 2 vs out-of-range→empty) for the count/page variants; (c) block-height filter for the tx methods; (d) SDK-ApiException → bloxbean-ApiException rethrow (one per new method — do NOT under-cover, per the recurring Phase-1 review gap); (e) the still-Unsupported methods still throw. Redeemer test: assert the Purpose→RedeemerTag mapping + ExecutionUnit→unitMem/steps + null-guarding when input/redeemer absent. `NexusPagination` gets its own small unit test (page boundaries).

## Open items for the plan

1. Confirm SDK `Purpose` enum values + bloxbean `RedeemerTag` values for the redeemer purpose map (read both enums).
2. Confirm SDK `ExecutionUnit` field names (mem/steps) + `Datum` shape (hash field) for the redeemer unit/datum mapping.
3. Confirm bloxbean `TxContentRedeemers.txIndex` semantics (redeemer list index vs tx-input index) — map to list index unless evidence says otherwise; document.
4. Confirm `getAddressTransactionHistory` pagination termination (`Pagination.hasNext`) + pick a max-page safety cap for `getAllTransactions`.
5. Confirm whether a reusable paginator already exists in cardano-client-lib before adding `NexusPagination`.
6. Confirm SDK `getAccountTransactions` `fromBlockHeight` semantics (inclusive?) for the range-filter.
