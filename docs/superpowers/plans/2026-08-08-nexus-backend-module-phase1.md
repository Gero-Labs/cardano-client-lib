# Nexus Backend Module (Phase 1 MVP) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `backend-modules/nexus` Gradle module to cardano-client-lib that implements bloxbean's `BackendService` by wrapping nexus-java-client 1.1.0 — a tx-capable provider with full Metadata/Script parity.

**Architecture:** Mirror the koios module. `NexusBackendService` holds a `Network` + an SDK `adlabs.nexus.client.backend.factory.BackendService`; each of 11 getters returns a `Nexus*Service` adapter that calls the matching SDK sub-service, converts the SDK `Result<T>` to bloxbean `Result<T>` (via a shared `NexusResultMapper`), and maps SDK models to bloxbean models. Gaps the SDK can't back throw `UnsupportedOperationException`. Unit tests mock the SDK services.

**Tech Stack:** Java 17, Gradle, nexus-java-client 1.1.0 (from `~/.m2` via mavenLocal), JUnit 5 + Mockito + AssertJ (already on every subproject's test classpath).

## Global Constraints

- Package `com.bloxbean.cardano.client.backend.nexus`. Java 17.
- Every bloxbean sub-service method returns `com.bloxbean.cardano.client.api.model.Result<T>` and throws `com.bloxbean.cardano.client.api.exception.ApiException`.
- SDK: services under `adlabs.nexus.client.backend.api.<domain>`, each method takes `adlabs.nexus.client.util.Network` first and returns `adlabs.nexus.client.backend.api.base.Result<T>` (`isSuccessful()`, `getCode()`, `getResponse()`, `getValue()`), throwing `adlabs.nexus.client.backend.api.base.exception.ApiException`. `Network.queryValue()` is used internally by the SDK — the adapter just passes the `Network` enum.
- Result conversion (shared `NexusResultMapper`): success → `Result.success("OK").withValue(model).code(200)`; SDK-unsuccessful → `Result.error(res.getResponse()).code(res.getCode())`; SDK `ApiException` caught → throw bloxbean `ApiException(e.getMessage(), e)`.
- SDK non-2xx is an unsuccessful `Result`, NOT a throw. Only transport/deserialize throws.
- Documented SDK gaps throw `new UnsupportedOperationException("<method> not supported by Nexus")`.
- Fixed `Network` passed into `NexusBackendService` ctor, threaded into every adapter.
- Version catalog dep: `nexus-java = "io.github.gero-labs:nexus-java-client:1.1.0"`, referenced `libs.nexus.java`. Resolved from `~/.m2` (mavenLocal).
- Tests: unit, mock the SDK sub-service (Mockito), under `backend-modules/nexus/src/test/java/...` (NOT `src/it`). Build one module: `./gradlew :backend-modules:nexus:test`.
- Mirror koios conventions (read `backend-modules/koios/.../Koios*Service.java`) for adapter shape.

---

## File structure

**Create (all under `backend-modules/nexus/`):**
- `build.gradle`
- `src/main/java/com/bloxbean/cardano/client/backend/nexus/`: `NexusBackendService`, `Constants`, `NexusResultMapper`, and `Nexus{Block,Network,Epoch,Transaction,Utxo,Address,Metadata,Script,Account,Asset,Pool}Service`.
- `src/test/java/com/bloxbean/cardano/client/backend/nexus/`: one `*Test` per adapter + `NexusBackendServiceTest`.

**Modify (root):**
- `settings.gradle` — add `include 'backend-modules:nexus'`.
- `gradle/libs.versions.toml` — add `nexus-java` line.
- `build.gradle` — add `mavenLocal()` to the subprojects `repositories` block (line ~122).

---

### Task 1: Module scaffold + NexusResultMapper + Constants

**Files:**
- Create `backend-modules/nexus/build.gradle`; modify `settings.gradle`, `gradle/libs.versions.toml`, root `build.gradle`.
- Create `NexusResultMapper.java`, `Constants.java`.
- Test: `src/test/java/.../NexusResultMapperTest.java`

**Interfaces:**
- Produces: `NexusResultMapper.map(adlabs...Result<S> res, java.util.function.Function<S,B> conv) -> com...Result<B>`; `Constants` with `MAINNET_URL`/`PREPROD_URL`/`PREVIEW_URL` (all `https://nexus.gerowallet.io` — Nexus routes by `?network=` query, so the base URL is the same; keep separate constants for clarity) + `DEFAULT_URL`.

- [ ] **Step 1: Wiring edits**
  - `settings.gradle`: add `include 'backend-modules:nexus'` after the koios/ogmios includes.
  - `gradle/libs.versions.toml`: add under `[libraries]`: `nexus-java = "io.github.gero-labs:nexus-java-client:1.1.0"`.
  - root `build.gradle` subprojects `repositories` block (~line 122, the one with `mavenCentral()`): add `mavenLocal()` as the FIRST repository so `io.github.gero-labs:nexus-java-client:1.1.0` resolves from `~/.m2`.
  - `backend-modules/nexus/build.gradle`:
    ```gradle
    dependencies {
        api project(':core')
        api project(':backend')
        api libs.nexus.java
    }
    publishing {
        publications {
            mavenJava(MavenPublication) {
                pom {
                    name = 'Cardano Client Nexus Backend'
                    description = 'Cardano Client Lib - Nexus Backend Module'
                }
            }
        }
    }
    ```
    (Do NOT add koios-style excludes yet — the SDK uses Retrofit/OkHttp/Jackson; add excludes only if Step 3's build shows a dependency clash.)

- [ ] **Step 2: Write the failing NexusResultMapper test**

```java
package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NexusResultMapperTest {
    @Test
    void map_success_convertsValueAndSetsOk() {
        adlabs.nexus.client.backend.api.base.Result<String> sdk =
            adlabs.nexus.client.backend.api.base.Result.success(200, "hi");
        Result<Integer> r = NexusResultMapper.map(sdk, String::length);
        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEqualTo(2);
        assertThat(r.code()).isEqualTo(200);
    }
    @Test
    void map_error_propagatesCodeAndResponse() {
        adlabs.nexus.client.backend.api.base.Result<String> sdk =
            adlabs.nexus.client.backend.api.base.Result.error(503, "down");
        Result<Integer> r = NexusResultMapper.map(sdk, String::length);
        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(503);
    }
}
```

- [ ] **Step 3: Run to verify fail + resolve the SDK dep**

Run: `./gradlew :backend-modules:nexus:test` → FAIL (NexusResultMapper missing). If it fails EARLIER on dependency resolution of `io.github.gero-labs:nexus-java-client:1.1.0`, confirm `~/.m2/repository/io/github/gero-labs/nexus-java-client/1.1.0/` has the jar (it was `mvn install`ed) and that `mavenLocal()` was added. Fix wiring until the failure is the missing test class, not a resolution error.

- [ ] **Step 4: Create `NexusResultMapper` + `Constants`**

```java
package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.Result;
import java.util.function.Function;

public final class NexusResultMapper {
    private NexusResultMapper() {}
    public static <S, B> Result<B> map(adlabs.nexus.client.backend.api.base.Result<S> res, Function<S, B> conv) {
        if (!res.isSuccessful()) return Result.<B>error(res.getResponse()).code(res.getCode());
        return Result.<B>success("OK").withValue(conv.apply(res.getValue())).code(200);
    }
}
```
(Verify the exact generic signatures of bloxbean `Result.success`/`error`/`withValue`/`code` in `core-api/.../api/model/Result.java` and adjust the generics to compile — koios uses the same fluent calls.)

`Constants`: `public static final String DEFAULT_URL = "https://nexus.gerowallet.io";` (+ MAINNET/PREPROD/PREVIEW URL constants = same value; a short comment: Nexus selects network via query param).

- [ ] **Step 5: Run to verify pass** — `./gradlew :backend-modules:nexus:test` → PASS.

- [ ] **Step 6: Commit** `feat(nexus): module scaffold + result mapper + constants`.

---

### Task 2: NexusBlockService + NexusNetworkService

**Files:** create `NexusBlockService.java`, `NexusNetworkService.java`; tests `NexusBlockServiceTest.java`, `NexusNetworkServiceTest.java`.

**Interfaces:**
- Consumes: SDK `adlabs...block.BlockService`, `adlabs...network.NetworkService`; `NexusResultMapper`; `Network`.
- Produces: `NexusBlockService implements BlockService`, `NexusNetworkService implements NetworkInfoService`.

- [ ] **Step 1: Write failing tests** (Mockito-mock the SDK service). Block:

```java
@Test
void getLatestBlock_maps() throws Exception {
    var sdkSvc = mock(adlabs.nexus.client.backend.api.block.BlockService.class);
    var sdkBlock = adlabs.nexus.client.backend.api.block.model.Block.builder()
        .hash("h1").height(100L).slot(5L).epoch(3).build();
    when(sdkSvc.getLatestBlock(any())).thenReturn(
        adlabs.nexus.client.backend.api.base.Result.success(200, sdkBlock));
    var svc = new NexusBlockService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
    Result<Block> r = svc.getLatestBlock();
    assertThat(r.isSuccessful()).isTrue();
    assertThat(r.getValue().getHash()).isEqualTo("h1");
    assertThat(r.getValue().getHeight()).isEqualTo(100L);
}
@Test
void getBlockByNumber_unsupported() {
    var svc = new NexusBlockService(mock(...BlockService.class), Network.MAINNET);
    assertThatThrownBy(() -> svc.getBlockByNumber(java.math.BigInteger.ONE))
        .isInstanceOf(UnsupportedOperationException.class);
}
```
Network test: mock `getNetworkInfo`, return an SDK `NetworkInfo` with the 10 fields, assert bloxbean `Genesis` mapping (note `systemStart`: SDK `Long` → bloxbean `Integer`; `slotLength`: SDK `Double` → bloxbean `Integer`; `activeSlotsCoefficient`: BigDecimal→BigDecimal).

- [ ] **Step 2: Run to verify fail** — `./gradlew :backend-modules:nexus:test --tests '*Block*' --tests '*Network*'` → FAIL.

- [ ] **Step 3: Implement NexusBlockService** — ctor `(adlabs...BlockService api, Network network)`; `getLatestBlock` → `NexusResultMapper.map(api.getLatestBlock(network), this::toBlock)`; `getBlockByHash(hash)` → `api.getBlock(network, hash)`; `getBlockByNumber` throws Unsupported. Wrap SDK `ApiException` → bloxbean. `toBlock(sdkBlock)`: build bloxbean `Block` from the 15 fields (all present; direct). Read both model classes to match field names/types.

- [ ] **Step 4: Implement NexusNetworkService** — `getNetworkInfo()` → map SDK `NetworkInfo`→bloxbean `Genesis`. Adapt `systemStart` Long→Integer (`sdk.getSystemStart() == null ? null : sdk.getSystemStart().intValue()`), `slotLength` Double→Integer (round or intValue — match bloxbean's Integer), rest direct.

- [ ] **Step 5: Run to verify pass**. **Step 6: Commit** `feat(nexus): block + network services`.

---

### Task 3: NexusEpochService (Epoch + ProtocolParams)

**Files:** `NexusEpochService.java`; test `NexusEpochServiceTest.java`.

**Interfaces:** Produces `NexusEpochService implements EpochService`. Consumes SDK `adlabs...epoch.EpochService`.

- [ ] **Step 1: Write failing tests** — mock SDK epoch service:
  - `getLatestEpoch()` → SDK `getLatestEpoch(network)` returns `Epoch`; assert `EpochContent` mapping (epoch, startTime, endTime, blockCount, txCount, output, fees, activeStake).
  - `getEpoch(Integer)` → assert `UnsupportedOperationException`.
  - `getProtocolParameters()` → SDK `getLatestEpochParameters(network)`; assert a spread of `ProtocolParams` fields mapped incl. a Conway one (e.g. `minFeeA`, `keyDeposit`, `priceMem`, `drepDeposit`, `costModels`).
  - `getProtocolParameters(Integer epoch)` → SDK `getEpochParams(network, epoch)`.

- [ ] **Step 2: Run to verify fail.**

- [ ] **Step 3: Implement** — `getLatestEpoch`/`getEpoch`: `getEpoch(Integer)` throws Unsupported; `getLatestEpoch` maps SDK `Epoch`→`EpochContent`. `getProtocolParameters()`/`(epoch)` map SDK `ProtocolParams`→bloxbean `ProtocolParams`. **The mapper is the big one:** read both `ProtocolParams` classes; map every field the SDK has (all ~52 incl. pvt*/dvt*/govActionDeposit/drepDeposit/committee*/costModels). Type adapts: SDK `String` deposits/minUtxo/minPoolCost/coinsPerUtxoSize/maxVal/exMem/exSteps → bloxbean's declared types (read each bloxbean field type; String→String direct, String→BigInteger via `new BigInteger(s)` where bloxbean uses BigInteger); `costModels` LinkedHashMap→ bloxbean `costModels`; bloxbean `epoch` field (if any) left null. Reference `KoiosEpochService.convertToProtocolParams` for the exhaustive field list + null-guards.

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): epoch + protocol params service`.

---

### Task 4: NexusTransactionService

**Files:** `NexusTransactionService.java`; test `NexusTransactionServiceTest.java`.

**Interfaces:** Produces `NexusTransactionService implements TransactionService`. Consumes SDK `adlabs...transaction.TransactionService`. Used later by `NexusUtxoService.getTxOutput`.

- [ ] **Step 1: Write failing tests:**
  - `submitTransaction(byte[])`: mock SDK `submitTransaction(network, cborHex)` returns `Result.success(200,"txhash")`; assert bloxbean `Result.getValue()=="txhash"` AND that the SDK was called with the HEX of the byte[] (use `HexUtil.encodeHexString` — the same util bloxbean uses; verify FQN `com.bloxbean.cardano.client.util.HexUtil`).
  - `getTransaction(hash)`: SDK `getTransaction`→`Transaction`; assert `TransactionContent` mapping (hash=`txHash`, block=`blockHash`, blockHeight, blockTime=`txTimestamp`, slot=`absoluteSlot`, fees=`fee`, deposit, size=`txSize`, invalidBefore, invalidHereafter=`invalidAfter`; `index`/`validContract` null; counts derived via list `.size()`).
  - `getTransactionUtxos(hash)`: SDK `getTransactionUtxos`→`TransactionUtxos`; assert `TxContentUtxo` inputs/outputs mapping (address, amount[unit,quantity], dataHash, inlineDatum, referenceScriptHash).
  - `getTransactionRedeemers(hash)`: assert `UnsupportedOperationException`.
  - `getTransactions(List)`: SDK has no batch getTransaction — assert it loops `getTransaction` per hash (mock two hashes, assert two results).

- [ ] **Step 2: Run to verify fail.**

- [ ] **Step 3: Implement** — `submitTransaction(byte[] cbor)` → `api.submitTransaction(network, HexUtil.encodeHexString(cbor))`, map `Result<String>` straight (value is the hash). `getTransaction`→`toTransactionContent`. `getTransactions(List)` loops (partition not needed for MVP; call per hash, collect). `getTransactionUtxos`→`toTxContentUtxo`. `getTransactionRedeemers` throws Unsupported. Inherit default `getTransactionOutput` + default-throwing `evaluateTx`. Read the SDK + bloxbean models for exact field names.

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): transaction service`.

---

### Task 5: NexusUtxoService (synth) + NexusAddressService

**Files:** `NexusUtxoService.java`, `NexusAddressService.java`; tests for each.

**Interfaces:** Produces `NexusUtxoService implements UtxoService` (ctor: SDK `address.AddressService` + `NexusTransactionService`), `NexusAddressService implements AddressService` (ctor: SDK `address.AddressService` + `Network`).

- [ ] **Step 1: Write failing tests:**
  - Utxo `getUtxos(address, count, page)`: mock SDK `getAddressUtxos(network, address, page=page, pageSize=count)` returns `List<AddressUtxo>` (2 utxos, one with an asset); assert bloxbean `List<Utxo>` mapping: `txHash`, `outputIndex=txIndex`, `address`, `amount` = `Amount(LOVELACE, new BigInteger(value))` + one `Amount(policyId+assetName or unit, quantity)` per `AssetBalance`, `dataHash`, `inlineDatum`=`InlineDatumValue.bytes`, `referenceScriptHash`=`ReferenceScriptValue.hash`. Assert the SDK was called with page/pageSize in the right order (page→page, count→pageSize).
  - Utxo `getUtxos(address, unit, count, page)`: SDK `getAddressUtxosByAsset(network,address,unit,page,count)`.
  - Utxo `getUtxos(...,order)`: order ignored (document) — assert it still returns mapped utxos (does NOT throw).
  - Utxo `getTxOutput(txHash, idx)`: delegates to the injected `NexusTransactionService.getTransactionOutput` — mock that path or assert delegation.
  - Utxo `isUsedAddress`: inherited default → assert `UnsupportedOperationException`.
  - Address `getAddressInfo(address)`: SDK `getAddressInformation`→`AddressInfo`; assert bloxbean `AddressContent` mapping (amount from balance+assets, stakeAddress, script=`scriptAddress`, type from `addressType`).
  - Address `getAddressDetails` → Unsupported; `getTransactions(address,count,page)` → SDK `getAddressTransactions(network,address,page,count)`→`List<AddressTransactionContent>`; `getAllTransactions` → Unsupported.

- [ ] **Step 2: Run to verify fail.**

- [ ] **Step 3: Implement NexusUtxoService** — mirror `KoiosUtxoService` but source from `getAddressUtxos`. `LOVELACE` = `com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE` (verify FQN). Build `Amount` list. `getUtxos(...,order)` overloads ignore order (1-line comment). `getTxOutput` → `transactionService.getTransactionOutput(txHash, idx)`.

- [ ] **Step 4: Implement NexusAddressService** — `getAddressInfo`→`toAddressContent`; `getAddressDetails`/`getAllTransactions` throw Unsupported; `getTransactions` maps.

- [ ] **Step 5: Run to verify pass.** **Step 6: Commit** `feat(nexus): utxo (synth) + address services`.

---

### Task 6: NexusMetadataService (full 5-method parity)

**Files:** `NexusMetadataService.java`; test `NexusMetadataServiceTest.java`.

**Interfaces:** Produces `NexusMetadataService implements MetadataService`. Consumes SDK `adlabs...metadata.MetadataService`.

- [ ] **Step 1: Write failing tests** — mock the SDK metadata service; assert all 5 methods map:
  - `getJSONMetadataByTxnHash(txnHash)` → SDK `getTxMetadata(network, txnHash)` returns `List<TxMetadataJson{label,json}>`; assert `List<MetadataJSONContent{txHash=txnHash, label, jsonMetadata=json}>`.
  - `getCBORMetadataByTxnHash` → SDK `getTxMetadataCbor`; assert `List<MetadataCBORContent{txHash,label,cborMetadata=cbor}>`.
  - `getMetadataLabels(count,page,order)` → SDK `getMetadataLabels(network, page, count)`; assert `List<MetadataLabel{label,cip10,count}>`; order ignored.
  - `getJSONMetadataByLabel(BigInteger label,count,page,order)` → SDK `getMetadataByLabel(network, label.toString(), page, count)`; assert `List<MetadataJSONContent{txHash, label=label.toString(), jsonMetadata=json}>`.
  - `getCBORMetadataByLabel(...)` → SDK `getMetadataCborByLabel`; assert `List<MetadataCBORContent>`.

- [ ] **Step 2: Run to verify fail.** **Step 3: Implement** all 5 (bloxbean `MetadataJSONContent(txHash,label,JsonNode)` / `MetadataCBORContent(txHash,label,String)` / `MetadataLabel(label,cip10,count)` ctors — verify field types: bloxbean `MetadataLabel.count` is `Integer`, SDK is `Long` → adapt). This is the service that gives Nexus FULL parity (koios throws on 3 of these).

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): metadata service (full parity)`.

---

### Task 7: NexusScriptService

**Files:** `NexusScriptService.java`; test `NexusScriptServiceTest.java`.

**Interfaces:** Produces `NexusScriptService implements ScriptService`. Consumes SDK `adlabs...script.ScriptService`.

- [ ] **Step 1: Write failing tests** — mock SDK script service:
  - `getScriptDatum(datumHash)` → SDK `getDatumByHash(network, datumHash)` returns `Datum{hash,cbor,json}`; assert bloxbean `ScriptDatum{jsonValue=json}`.
  - `getScriptDatumCbor(datumHash)` → same SDK call; assert `ScriptDatumCbor{cbor}`.
  - `getNativeScriptJson(scriptHash)` → SDK `getScriptByHash(network, scriptHash)` returns `ScriptDetail{type=native,json}`; assert `Result<JsonNode>` = the json.
  - `getPlutusScriptCbor(scriptHash)` → SDK `getScriptByHash`; assert `Result<String>` = `ScriptDetail.cbor`.

- [ ] **Step 2: Run to verify fail.** **Step 3: Implement** the 4 methods; inherit default `getNativeScript`/`getPlutusScript`.

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): script service`.

---

### Task 8: NexusAccountService + NexusAssetService + NexusPoolService (primary-method-only)

**Files:** the 3 service classes + 3 tests.

**Interfaces:** Produces the 3 adapters. SDK primary reads (verified): account `getAccountInformation(network, stakeAddress)`; asset `getAssetDetailedInformation(network, assetPolicy, assetName)`; pool `getPool(network, poolId)`.

- [ ] **Step 1: Write failing tests:**
  - Account: `getAccountInformation(stakeAddress)` maps SDK `AccountInformation`→bloxbean `AccountInformation` (active, controlledAmount, rewardsSum, …, pool_id). All OTHER AccountService methods → assert `UnsupportedOperationException`.
  - Asset: `getAsset(unit)` — split `unit` into policyId (first 56 hex chars) + assetName (rest); call SDK `getAssetDetailedInformation(network, policyId, assetName)`; map SDK `AssetDetailedInformation`→bloxbean `Asset` (asset, policyId, assetName, fingerprint, quantity, onchainMetadata, metadata — map what the SDK carries; absent→null). All OTHER AssetService methods → Unsupported.
  - Pool: `getPoolInfo(poolId)` → SDK `getPool(network, poolId)` → map `Pool`→bloxbean `PoolInfo` (poolId, hex, vrfKey, blocksMinted, liveStake, …, owners — map what SDK carries). (PoolService has only this one method.)

- [ ] **Step 2: Run to verify fail.** **Step 3: Implement** the 3 adapters. Read the SDK `AccountInformation`/`AssetDetailedInformation`/`Pool` models + bloxbean `AccountInformation`/`Asset`/`PoolInfo` for the field mapping; map present fields, null the rest. Throw Unsupported on the non-primary methods.

- [ ] **Step 4: Run to verify pass.** **Step 5: Commit** `feat(nexus): account + asset + pool services (primary reads)`.

---

### Task 9: NexusBackendService wiring + full module build

**Files:** `NexusBackendService.java`; test `NexusBackendServiceTest.java`.

**Interfaces:** Produces `NexusBackendService implements BackendService` — ctor `(String baseUrl, String apiKey, Network network)` (+ convenience `(Network)` using `Constants.DEFAULT_URL` and a null/empty key if the SDK allows). Builds the SDK backend via `adlabs.nexus.client.backend.factory.BackendServiceFactory.getNexusBackendService(baseUrl, apiKey)` once; each getter returns the matching adapter (constructed with the SDK sub-service(s) + `Network`).

- [ ] **Step 1: Write the failing test** — construct `new NexusBackendService(Constants.DEFAULT_URL, "key", Network.PREPROD)`; assert all 11 getters (`getAssetService`, `getBlockService`, `getNetworkInfoService`, `getPoolService`, `getTransactionService`, `getUtxoService`, `getAddressService`, `getAccountService`, `getEpochService`, `getMetadataService`, `getScriptService`) return non-null. (No network calls — just wiring.)

- [ ] **Step 2: Run to verify fail.**

- [ ] **Step 3: Implement** — mirror `KoiosBackendService`: hold `sdkBackend` + `Network`; each getter `new Nexus*Service(sdkBackend.getXxxService(), network[, deps])`. Note the multi-dep ones: `getUtxoService()` → `new NexusUtxoService(sdkBackend.getAddressService(), (NexusTransactionService) getTransactionService(), network)`. Do NOT override the default helper/fee methods.

- [ ] **Step 4: Run to verify pass.**

- [ ] **Step 5: Full module build** — `./gradlew :backend-modules:nexus:test` → all adapter tests + wiring test green. Then `./gradlew :backend-modules:nexus:build` → module compiles + jars. Confirm no dependency-resolution or compile errors across the module.

- [ ] **Step 6: Commit** `feat(nexus): backend service wiring + module assembly`.

---

## Self-review

**Spec coverage:** module scaffold + wiring + mavenLocal + ResultMapper + Constants → Task 1. Block/Network → Task 2. Epoch/ProtocolParams → Task 3. Transaction (+submit hex, +utxos, redeemers Unsupported) → Task 4. Utxo synth + Address → Task 5. Metadata full parity → Task 6. Script → Task 7. Account/Asset/Pool primary-only → Task 8. NexusBackendService 11-getter wiring + full build → Task 9. Testing (mock SDK, mapping + Result + Unsupported) → every task. Documented gaps (block-by-number, epoch-by-number, redeemers, address details/all, isUsedAddress, order) → Tasks 2/3/4/5.

**Placeholder scan:** no TBD/TODO. Field-level mappings say "read both model classes + map present fields, null the rest" with the key correspondences + type adaptations named (systemStart Long→Integer, slotLength Double→Integer, label BigInteger→String, cbor byte[]→hex, MetadataLabel.count Long→Integer) — concrete, not vague. FQN-verify steps (HexUtil, LOVELACE, Result generics) are explicit compile-guards, not placeholders.

**Type consistency:** adapters implement the exact bloxbean sub-service interfaces; `NexusResultMapper.map` signature consistent across all; `NexusBackendService` getters return the adapter types built in Tasks 2-8; `NexusTransactionService` is consumed by `NexusUtxoService` (Task 5) and constructed in Task 9. Network threaded uniformly.
