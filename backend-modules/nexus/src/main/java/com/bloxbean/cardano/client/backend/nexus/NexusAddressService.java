package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressInfo;
import adlabs.nexus.client.backend.api.address.model.AddressTransaction;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;

import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Nexus Address Service
 */
public class NexusAddressService implements com.bloxbean.cardano.client.backend.api.AddressService {

    private final adlabs.nexus.client.backend.api.address.AddressService addressService;
    private final Network network;

    public NexusAddressService(adlabs.nexus.client.backend.api.address.AddressService addressService, Network network) {
        this.addressService = addressService;
        this.network = network;
    }

    @Override
    public Result<AddressContent> getAddressInfo(String address) throws ApiException {
        try {
            return NexusResultMapper.map(addressService.getAddressInformation(network, address), this::toAddressContent);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<AddressDetails> getAddressDetails(String address) throws ApiException {
        throw new UnsupportedOperationException("getAddressDetails not supported by Nexus");
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page) throws ApiException {
        try {
            return NexusResultMapper.map(addressService.getAddressTransactions(network, address, page, count),
                    this::toAddressTransactionContents);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // Nexus has no order param; delegate as-is.
    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException {
        return getTransactions(address, count, page);
    }

    @Override
    public Result<List<AddressTransactionContent>> getAllTransactions(String address, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        throw new UnsupportedOperationException("getAllTransactions not supported by Nexus");
    }

    private AddressContent toAddressContent(AddressInfo addressInfo) {
        AddressContent addressContent = new AddressContent();
        addressContent.setStakeAddress(addressInfo.getStakeAddress());
        addressContent.setScript(addressInfo.getScriptAddress());
        addressContent.setType("shelley".equalsIgnoreCase(addressInfo.getAddressType())
                ? AddressContent.TypeEnum.SHELLEY : AddressContent.TypeEnum.BYRON);

        List<TxContentOutputAmount> amount = new ArrayList<>();
        amount.add(new TxContentOutputAmount(LOVELACE, addressInfo.getBalance()));
        if (addressInfo.getAssets() != null) {
            for (AssetBalance asset : addressInfo.getAssets()) {
                String unit = asset.getUnit() != null ? asset.getUnit() : asset.getPolicyId() + asset.getAssetName();
                amount.add(new TxContentOutputAmount(unit, asset.getQuantity()));
            }
        }
        addressContent.setAmount(amount);
        return addressContent;
    }

    private List<AddressTransactionContent> toAddressTransactionContents(List<AddressTransaction> txs) {
        List<AddressTransactionContent> result = new ArrayList<>();
        for (AddressTransaction tx : txs) {
            result.add(AddressTransactionContent.builder()
                    .txHash(tx.getTxHash())
                    .txIndex(tx.getTxIndex() == null ? 0 : tx.getTxIndex())
                    .blockHeight(tx.getBlockHeight() == null ? 0L : tx.getBlockHeight())
                    .blockTime(tx.getBlockTime() == null ? 0L : tx.getBlockTime())
                    .build());
        }
        return result;
    }
}
