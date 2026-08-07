package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Nexus Asset Service. Only {@link #getAsset(String)} is backed by the SDK
 * (getAssetDetailedInformation); the other AssetService methods have no Nexus SDK equivalent yet.
 */
public class NexusAssetService implements AssetService {

    // policy id is a blake2b-224 hash: 28 bytes = 56 hex chars.
    private static final int POLICY_ID_HEX_LENGTH = 56;

    private final adlabs.nexus.client.backend.api.asset.AssetService assetService;
    private final Network network;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NexusAssetService(adlabs.nexus.client.backend.api.asset.AssetService assetService, Network network) {
        this.assetService = assetService;
        this.network = network;
    }

    @Override
    public Result<Asset> getAsset(String unit) throws ApiException {
        if (unit == null || unit.length() < POLICY_ID_HEX_LENGTH) {
            throw new ApiException("Invalid asset unit: " + unit);
        }
        String policyId = unit.substring(0, POLICY_ID_HEX_LENGTH);
        String assetName = unit.substring(POLICY_ID_HEX_LENGTH);
        try {
            return NexusResultMapper.map(assetService.getAssetDetailedInformation(network, policyId, assetName),
                    info -> toAsset(unit, info));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Asset toAsset(String unit, adlabs.nexus.client.backend.api.asset.model.AssetDetailedInformation info) {
        Asset asset = new Asset();
        asset.setAsset(unit);
        asset.setPolicyId(info.getPolicyId());
        asset.setAssetName(info.getAssetName());
        asset.setFingerprint(info.getFingerprint());
        asset.setQuantity(info.getQuantity());
        asset.setInitialMintTxHash(info.getInitialMintTxHash());
        asset.setMintOrBurnCount(info.getMintOrBurnCount());
        asset.setOnchainMetadata(parseOnchainMetadata(info.getOnchainMetadata()));
        if (info.getMetadata() != null) {
            asset.setMetadata(objectMapper.convertValue(info.getMetadata(), JsonNode.class));
        }
        return asset;
    }

    // SDK carries onchain metadata (CIP-25/label-721) as a raw JSON string; bloxbean wants a JsonNode.
    private JsonNode parseOnchainMetadata(String onchainMetadata) {
        if (onchainMetadata == null || onchainMetadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(onchainMetadata);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Override
    public Result<List<AssetAddress>> getAllAssetAddresses(String asset) throws ApiException {
        throw new UnsupportedOperationException("getAllAssetAddresses not supported by Nexus");
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAssetAddresses not supported by Nexus");
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAssetAddresses not supported by Nexus");
    }

    @Override
    public Result<List<PolicyAsset>> getAllPolicyAssets(String policyId) throws ApiException {
        throw new UnsupportedOperationException("getAllPolicyAssets not supported by Nexus");
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getPolicyAssets not supported by Nexus");
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getPolicyAssets not supported by Nexus");
    }

    @Override
    public Result<List<AssetTransactionContent>> getTransactions(String asset, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getTransactions not supported by Nexus");
    }
}
