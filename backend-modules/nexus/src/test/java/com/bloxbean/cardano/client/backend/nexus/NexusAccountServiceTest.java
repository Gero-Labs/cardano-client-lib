package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.account.model.AccountInformation;
import com.bloxbean.cardano.client.api.model.Result;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusAccountServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    @Test
    void getAccountInformation_maps() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        AccountInformation info = AccountInformation.builder()
                .active(true)
                .controlledAmount("1000000")
                .rewardsSum("5000")
                .reservesSum("0")
                .withdrawalsSum("1000")
                .treasurySum("0")
                .withdrawableAmount("4000")
                .poolId("pool1abc")
                .build();
        when(sdkAccountSvc.getAccountInformation(eq(NET), eq("stake1xyz")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<com.bloxbean.cardano.client.backend.model.AccountInformation> r = svc.getAccountInformation("stake1xyz");

        assertThat(r.isSuccessful()).isTrue();
        com.bloxbean.cardano.client.backend.model.AccountInformation ai = r.getValue();
        assertThat(ai.getActive()).isTrue();
        assertThat(ai.getControlledAmount()).isEqualTo("1000000");
        assertThat(ai.getRewardsSum()).isEqualTo("5000");
        assertThat(ai.getReservesSum()).isEqualTo("0");
        assertThat(ai.getWithdrawalsSum()).isEqualTo("1000");
        assertThat(ai.getTreasurySum()).isEqualTo("0");
        assertThat(ai.getWithdrawableAmount()).isEqualTo("4000");
        assertThat(ai.getPool_id()).isEqualTo("pool1abc");
    }

    @Test
    void getAccountInformation_error_propagates() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountInformation(any(), any()))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);
        Result<com.bloxbean.cardano.client.backend.model.AccountInformation> r = svc.getAccountInformation("stake1missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAccountInformation_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        when(sdkAccountSvc.getAccountInformation(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAccountInformation("stake1xyz"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getAccountRewardsHistory_unsupported() {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAccountRewardsHistory("stake1xyz", 10, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllAccountAddresses_unsupported() {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAllAccountAddresses("stake1xyz"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllAccountTransactions_unsupported() {
        var sdkAccountSvc = mock(adlabs.nexus.client.backend.api.account.AccountService.class);
        var svc = new NexusAccountService(sdkAccountSvc, NET);

        assertThatThrownBy(() -> svc.getAllAccountTransactions("stake1xyz", null, 1, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
