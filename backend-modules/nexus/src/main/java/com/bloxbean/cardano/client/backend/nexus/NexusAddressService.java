package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressInfo;
import adlabs.nexus.client.backend.api.address.model.AddressTransaction;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import adlabs.nexus.client.backend.api.address.model.Pagination;
import adlabs.nexus.client.backend.api.address.model.TransactionHistoryItem;
import adlabs.nexus.client.backend.api.address.model.TransactionHistoryResponse;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentOutputAmount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;

/**
 * Nexus Address Service
 */
public class NexusAddressService implements com.bloxbean.cardano.client.backend.api.AddressService {

    private static final Logger log = LoggerFactory.getLogger(NexusAddressService.class);

    // Client-side defaults (not SDK-mandated): page size for the history fetch,
    // and a hard safety cap bounding the page-loop below.
    private static final int ALL_TRANSACTIONS_PAGE_SIZE = 100;
    private static final int ALL_TRANSACTIONS_MAX_PAGES = 1000;

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
        // Blockfrost defaults to ascending (oldest first).
        return getTransactions(address, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException {
        return getTransactions(address, count, page, order, null, null);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order, String fromBlockHeight, String toBlockHeight) throws ApiException {
        Integer from = (fromBlockHeight == null || fromBlockHeight.isEmpty()) ? null : Integer.valueOf(fromBlockHeight);
        Integer to = (toBlockHeight == null || toBlockHeight.isEmpty()) ? null : Integer.valueOf(toBlockHeight);
        try {
            return NexusResultMapper.map(
                    addressService.getAddressTransactions(network, address, page, count, from, to, orderStr(order)),
                    this::toAddressTransactionContents);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private static String orderStr(OrderEnum order) {
        return order == OrderEnum.desc ? "desc" : "asc";
    }

    // Server-side block-range + order (Nexus 1.3+): page the range endpoint until a short page.
    @Override
    public Result<List<AddressTransactionContent>> getAllTransactions(String address, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        List<AddressTransactionContent> all = new ArrayList<>();
        try {
            int page = 1;
            while (page <= ALL_TRANSACTIONS_MAX_PAGES) {
                adlabs.nexus.client.backend.api.base.Result<List<AddressTransaction>> pageResult =
                        addressService.getAddressTransactions(network, address, page, ALL_TRANSACTIONS_PAGE_SIZE, fromBlockHeight, toBlockHeight, orderStr(order));
                if (!pageResult.isSuccessful()) {
                    return Result.error(pageResult.getResponse()).code(pageResult.getCode());
                }
                List<AddressTransaction> body = pageResult.getValue();
                if (body == null || body.isEmpty()) {
                    break;
                }
                all.addAll(toAddressTransactionContents(body));
                if (body.size() < ALL_TRANSACTIONS_PAGE_SIZE) {
                    break;
                }
                page++;
            }
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
        return Result.success("OK").withValue(all).code(200);
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
