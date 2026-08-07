# Nexus Backend Module (Phase 1 MVP) — Design

Date: 2026-08-08
Status: Approved design — ready for implementation plan
Layer: 3 of 3 (final). Upstream: nexus-java-client SDK 1.1.0 (Gero-Labs/nexus-java-client PR #6). Lives in a fork of bloxbean/cardano-client-lib (`Gero-Labs/cardano-client-lib`, branch `feat/nexus-backend-module`).

## Why

Completes the goal "add a Nexus provider to cardano-client-lib": a new `backend-modules/nexus`
Gradle module implementing bloxbean's `BackendService` by wrapping the nexus-java-client SDK.
Mirrors the existing `koios` module structure exactly.

## Scope (Phase 1 MVP)

A **usable, tx-capable** provider. Every one of bloxbean's 11 sub-services is present (the
`BackendService` interface requires all 11 getters), but method coverage is scoped to what the
SDK backs today:

- **Full MVP services:** Block, NetworkInfo, Epoch (+ProtocolParams), Transaction (+submit,
  +utxos), Utxo (synthesized), Address, Metadata, Script.
- **Primary-method-only (Phase 1):** Account (`getAccountInformation`), Asset (`getAsset`),
  Pool (`getPoolInfo`) — the single core read each; all other overloads throw
  `UnsupportedOperationException`.
- **Documented gaps** (SDK doesn't expose the data) throw `UnsupportedOperationException`, exactly
  as koios does for its gaps.

**Phase 2 (deferred, separate spec):** flesh out Account/Asset/Pool full method sets, Address
history overloads, and any richer coverage.

Out of scope: any SDK or server change; live integration tests (no reachable Nexus preprod here).

## Module structure (mirror koios)

Package `com.bloxbean.cardano.client.backend.nexus`. Files:
- `NexusBackendService implements BackendService` — holds a `Network` + an SDK
  `adlabs.nexus.client.backend.factory.BackendService` (built via
  `BackendServiceFactory.getNexusBackendService(baseUrl, apiKey)`); each of the 11 getters
  returns the matching `Nexus*Service` adapter.
- `Nexus{Asset,Block,Network,Pool,Transaction,Utxo,Address,Account,Epoch,Metadata,Script}Service`
  — each takes the SDK sub-service(s) + the `Network` in its constructor.
- `Constants` — per-network base URLs (mainnet/preprod/preview) + the default `https://nexus.gerowallet.io`.
- `NexusResultMapper` (small helper) — the SDK→bloxbean `Result` conversion + `ApiException` wrap,
  reused by every adapter.

### Build wiring
- `backend-modules/nexus/build.gradle`: `api project(':core')`, `api project(':backend')`,
  `api libs.nexus.java` (with the same slf4j/bouncycastle/jackson exclusions koios uses IF the
  SDK bundles them — verify at plan time); publishing block (`name = 'Cardano Client Nexus Backend'`).
- `settings.gradle`: add `include 'backend-modules:nexus'`.
- `gradle/libs.versions.toml`: `nexus-java = "io.github.gero-labs:nexus-java-client:1.1.0"`.
- **Dependency resolution:** the SDK is not on Maven Central yet — it's installed to `~/.m2`
  (`mvn install`), and the root build must resolve it via `mavenLocal()`. Confirm the root
  `build.gradle` repositories include `mavenLocal()` (add if absent, scoped so it doesn't slow
  other modules); swap to the Central coordinate once the SDK is released.

## Plumbing

**Network in constructor.** bloxbean sub-service methods carry NO network arg; the SDK's carry
`Network` on every call. `NexusBackendService` fixes one `adlabs.nexus.client.util.Network` at
construction and passes it into each adapter, which threads it into every SDK call.

**Result conversion** (`NexusResultMapper`, mirrors koios):
```java
static <S, B> Result<B> map(adlabs...Result<S> res, Function<S,B> conv) {
    if (!res.isSuccessful()) return Result.error(res.getResponse()).code(res.getCode());
    return Result.success("OK").withValue(conv.apply(res.getValue())).code(200);
}
```
The SDK returns non-2xx as an unsuccessful `Result` (not a throw); only transport/deserialize
failures throw the SDK `ApiException` — adapters catch it and rethrow bloxbean
`com.bloxbean.cardano.client.api.exception.ApiException(e.getMessage(), e)`.

**Constants + factory.** `NexusBackendService(String baseUrl, String apiKey, Network network)`
(+ convenience ctors for preprod/mainnet using `Constants`). Builds the SDK backend once, shares
it across adapters.

## Per-service mapping (Phase 1)

### BlockService → SDK `block`
| bloxbean | SDK | notes |
|---|---|---|
| `getLatestBlock()` | `getLatestBlock(network)` | map SDK `Block`→bloxbean `Block` (all 15 fields present) |
| `getBlockByHash(hash)` | `getBlock(network, hash)` | same mapper |
| `getBlockByNumber(BigInteger)` | — | **UnsupportedOperationException** (SDK has no by-number) |

Mapper `Block`: time/height/slot/hash/slotLeader/output/fees/blockVrf(`Vrf.output`? use SDK
`blockVrf` String)/previousBlock/nextBlock/epoch/epochSlot/size/txCount/confirmations — direct.

### NetworkInfoService → SDK `network`
| `getNetworkInfo()` | `getNetworkInfo(network)` | map SDK `NetworkInfo`→bloxbean `Genesis`; all 10 fields present. Adapt: `systemStart` Long epoch-seconds → bloxbean `String` (ISO or numeric — match bloxbean `Genesis.systemStart` type at plan time), `activeSlotsCoefficient` BigDecimal→ bloxbean type. |

### EpochService → SDK `epoch`
| `getLatestEpoch()` | `getLatestEpoch(network)` | map SDK `Epoch`→`EpochContent` (epoch/startTime/endTime/blockCount/txCount/output/fees/activeStake; bloxbean fields absent in SDK → null) |
| `getEpoch(Integer)` | — | **UnsupportedOperationException** (SDK: latest only) |
| `getProtocolParameters()` | `getLatestEpochParameters(network)` | map SDK `ProtocolParams`→bloxbean `ProtocolParams` |
| `getProtocolParameters(Integer)` | `getEpochParams(network, epoch)` | same mapper |

ProtocolParams mapper: SDK has all ~52 fields incl. Conway gov (pvt*/dvt*/govActionDeposit/
drepDeposit/etc.) — map field-for-field. Type adapts: SDK Strings (keyDeposit/poolDeposit/minUtxo/
minPoolCost/coinsPerUtxoSize/maxVal/exMem/exSteps) → bloxbean's declared types (String/BigInteger —
match each at plan time); `costModels` nested `LinkedHashMap<String,LinkedHashMap<String,Long>>`
→ bloxbean `costModels`; bloxbean `epoch` field on ProtocolParams (if present) left null (SDK
params carry no epoch).

### TransactionService → SDK `transaction`
| `submitTransaction(byte[] cbor)` | `submitTransaction(network, hex(cbor))` | **hex-encode** the byte[] → SDK returns tx hash String |
| `getTransaction(hash)` | `getTransaction(network, hash)` | map SDK `Transaction`→`TransactionContent`. Absent: `index`→null, `validContract`→null; `outputAmount` list → synthesize by aggregating `outputs[].assetList`+lovelace, OR leave the lovelace-only `totalOutput`; count fields → derive via `list.size()` (utxoCount from outputs, withdrawalCount from withdrawals, etc.) |
| `getTransactions(List)` | loop `getTransaction` per hash (SDK has no batch getTransaction, only batch cbor) | acceptable for MVP; document N calls |
| `getTransactionUtxos(hash)` | `getTransactionUtxos(network, hash)` | map SDK `TransactionUtxos{hash,inputs,outputs}`→bloxbean `TxContentUtxo` (inputs/outputs: address=`Utxo.address`, amount from `Utxo.amount[unit,quantity]`, dataHash, inlineDatum, referenceScriptHash) |
| `getTransactionRedeemers(hash)` | — | **UnsupportedOperationException** (SDK Transaction carries `plutusContracts` but not the bloxbean redeemer shape; defer to Phase 2) |
| `getTransactionOutput(hash, idx)` (default) | inherited default (uses `getTransactionUtxos`) | free |
| `evaluateTx` (default) | inherited default-throws | free |

### UtxoService → **synthesized from SDK `address`**
No SDK utxo endpoint. Mirror `KoiosUtxoService` but source from `getAddressUtxos`:
| `getUtxos(address, count, page)` | `addressService.getAddressUtxos(network, address, page, count)` | SDK arg order is `(page, pageSize)` → pass page→page, count→pageSize; map each `AddressUtxo`→bloxbean `Utxo` (txHash, outputIndex=`txIndex`, address, amount = lovelace `Amount(LOVELACE, value)` + one per `AssetBalance`, dataHash, inlineDatum=`InlineDatumValue.bytes`, referenceScriptHash=`ReferenceScriptValue.hash`) |
| `getUtxos(address, count, page, order)` | same, **order ignored** (SDK has no order) — document; OR throw Unsupported. Decision: **ignore order, document** (koios sorts in-memory; MVP keeps server order). |
| `getUtxos(address, unit, count, page[, order])` | `getAddressUtxosByAsset(network, address, unit, page, count)` | map same |
| `getTxOutput(txHash, idx)` | delegate to `NexusTransactionService.getTransactionOutput` | free-ish |
| `isUsedAddress` (default) | inherited default-throws | free |

### AddressService → SDK `address`
| `getAddressInfo(address)` | `getAddressInformation(network, address)` | map SDK `AddressInfo{address,stakeAddress,scriptAddress,addressType,balance,assets,utxos}`→bloxbean `AddressContent` (map amount list from balance+assets; fields bloxbean has that SDK lacks → null) |
| `getAddressDetails(address)` | — | **UnsupportedOperationException** (SDK has no received/sent/tx-count rollup) |
| `getTransactions(address,count,page[,order])` | `getAddressTransactions(network,address,page,count)` | map `AddressTransaction`→`AddressTransactionContent`; order ignored/documented |
| `getAllTransactions(...)` | — | **UnsupportedOperationException** (Phase 2) |

### MetadataService → SDK `metadata` (clean 1:1 — L1/L2 purpose-built)
| `getJSONMetadataByTxnHash(txnHash)` | `getTxMetadata(network, txnHash)` | map `List<TxMetadataJson{label,json}>`→`List<MetadataJSONContent{txHash,label,jsonMetadata}>` (txHash = the queried hash) |
| `getCBORMetadataByTxnHash(txnHash)` | `getTxMetadataCbor(network, txnHash)` | →`List<MetadataCBORContent{txHash,label,cborMetadata}>` |
| `getMetadataLabels(count,page,order)` | `getMetadataLabels(network, page, count)` | →`List<MetadataLabel{label,cip10,count}>`; order ignored/documented |
| `getJSONMetadataByLabel(label,count,page,order)` | `getMetadataByLabel(network, label.toString(), page, count)` | →`List<MetadataJSONContent{txHash,label,json}>` (label = the queried label) |
| `getCBORMetadataByLabel(label,count,page,order)` | `getMetadataCborByLabel(network, label.toString(), page, count)` | →`List<MetadataCBORContent>` |

Note: bloxbean label is `BigInteger` → `.toString()` for the SDK's `String` label param. This is
the service that finally gives Nexus **full** bloxbean MetadataService parity (koios throws on 3 of these).

### ScriptService → SDK `script`
| `getScriptDatum(datumHash)` | `getDatumByHash(network, datumHash)` | map SDK `Datum{hash,cbor,json}`→bloxbean `ScriptDatum{jsonValue=json}` |
| `getScriptDatumCbor(datumHash)` | `getDatumByHash(network, datumHash)` | →`ScriptDatumCbor{cbor}` |
| `getNativeScriptJson(scriptHash)` | `getScriptByHash(network, scriptHash)` | →`JsonNode` = SDK `ScriptDetail.json` (native scripts) |
| `getPlutusScriptCbor(scriptHash)` | `getScriptByHash(network, scriptHash)` | →`String` = SDK `ScriptDetail.cbor` (plutus) |
| `getNativeScript` / `getPlutusScript` (defaults) | inherited | free (built on the two above) |

### Account/Asset/Pool (primary-method-only, Phase 1)
- `AccountService.getAccountInformation(stakeAddress)` ← SDK account service primary read (map to
  bloxbean `AccountInformation`); all other AccountService methods → `UnsupportedOperationException`.
- `AssetService.getAsset(unit)` ← SDK asset service primary read → bloxbean `Asset`; rest Unsupported.
- `PoolService.getPoolInfo(poolId)` ← SDK pool service primary read → bloxbean `PoolInfo`; (PoolService
  has only this one method).
- Exact SDK method names + model field mapping for these three are pinned in the plan (the MVP audit
  focused on Block/Network/Epoch/Transaction/Address; Account/Asset/Pool get a quick SDK-method check
  in the plan's first Account/Asset/Pool task).

## Testing (unit, mock SDK)

koios uses live-preprod ITs; we can't (no Nexus preprod here). Instead, per adapter, a JUnit 5 unit
test that **mocks the SDK sub-service** (Mockito) and asserts:
- the adapter calls the right SDK method with the fixed `Network` + translated args (arg order
  page/pageSize, hex-encoded cbor, `label.toString()`),
- SDK success `Result` → bloxbean `Result.isSuccessful()` + correct model field mapping,
- SDK unsuccessful `Result` (non-2xx) → bloxbean `Result.isSuccessful()==false` + code propagated,
- documented gaps throw `UnsupportedOperationException`,
- SDK `ApiException` → bloxbean `ApiException`.
Plus a `NexusBackendServiceTest` asserting all 11 getters return non-null wired adapters.

Mockito is already a test dependency of the root build (verify at plan time); tests live under
`backend-modules/nexus/src/test/java/...` (unit, not the `src/it` integration suite koios uses).

A **live smoke** (point at a running Nexus + a known preprod address/tx) is deferred to the human,
like the L1 smoke.

## Open items for the plan

1. Confirm root `build.gradle` has (or add) `mavenLocal()` so `libs.nexus.java` resolves from `~/.m2`.
2. Confirm bloxbean model exact field types where the SDK differs (Genesis.systemStart, ProtocolParams
   String-vs-BigInteger fields, AddressContent amount shape) — pin each mapping.
3. Audit the SDK account/asset/pool service method names + models for the three primary-method mappings.
4. Confirm whether the SDK jar bundles slf4j/bouncycastle/jackson (→ needs the koios-style exclusions)
   or is clean (Retrofit/OkHttp/Jackson — likely needs the jackson excludes only if versions clash).
5. Confirm Mockito availability in the module's test classpath.
