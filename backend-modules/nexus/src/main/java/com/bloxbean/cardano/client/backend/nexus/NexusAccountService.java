package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AccountService;
import com.bloxbean.cardano.client.backend.model.*;

import java.util.List;

/**
 * Nexus Account Service. Only {@link #getAccountInformation(String)} is backed by the SDK
 * (getAccountInformation); the other AccountService methods have no Nexus SDK equivalent yet.
 */
public class NexusAccountService implements AccountService {

    private final adlabs.nexus.client.backend.api.account.AccountService accountService;
    private final Network network;

    public NexusAccountService(adlabs.nexus.client.backend.api.account.AccountService accountService, Network network) {
        this.accountService = accountService;
        this.network = network;
    }

    @Override
    public Result<AccountInformation> getAccountInformation(String stakeAddress) throws ApiException {
        try {
            return NexusResultMapper.map(accountService.getAccountInformation(network, stakeAddress), this::toAccountInformation);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private AccountInformation toAccountInformation(adlabs.nexus.client.backend.api.account.model.AccountInformation info) {
        AccountInformation accountInformation = new AccountInformation();
        accountInformation.setActive(info.getActive());
        accountInformation.setControlledAmount(info.getControlledAmount());
        accountInformation.setRewardsSum(info.getRewardsSum());
        accountInformation.setReservesSum(info.getReservesSum());
        accountInformation.setWithdrawalsSum(info.getWithdrawalsSum());
        accountInformation.setTreasurySum(info.getTreasurySum());
        accountInformation.setWithdrawableAmount(info.getWithdrawableAmount());
        accountInformation.setPool_id(info.getPoolId());
        return accountInformation;
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAccountRewardsHistory not supported by Nexus");
    }

    @Override
    public Result<List<AccountRewardsHistory>> getAccountRewardsHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAccountRewardsHistory not supported by Nexus");
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAccountHistory not supported by Nexus");
    }

    @Override
    public Result<List<AccountHistory>> getAccountHistory(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAccountHistory not supported by Nexus");
    }

    @Override
    public Result<List<AccountAddress>> getAllAccountAddresses(String stakeAddress) throws ApiException {
        throw new UnsupportedOperationException("getAllAccountAddresses not supported by Nexus");
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAccountAddresses not supported by Nexus");
    }

    @Override
    public Result<List<AccountAddress>> getAccountAddresses(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAccountAddresses not supported by Nexus");
    }

    @Override
    public Result<List<AccountAsset>> getAllAccountAssets(String stakeAddress) throws ApiException {
        throw new UnsupportedOperationException("getAllAccountAssets not supported by Nexus");
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getAccountAssets not supported by Nexus");
    }

    @Override
    public Result<List<AccountAsset>> getAccountAssets(String stakeAddress, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getAccountAssets not supported by Nexus");
    }

    @Override
    public Result<List<AddressTransactionContent>> getAccountTransactions(String stakeAddress, int count, int page, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        throw new UnsupportedOperationException("getAccountTransactions not supported by Nexus");
    }

    @Override
    public Result<List<AddressTransactionContent>> getAllAccountTransactions(String stakeAddress, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        throw new UnsupportedOperationException("getAllAccountTransactions not supported by Nexus");
    }
}
