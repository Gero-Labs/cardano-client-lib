package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.address.model.AddressInfo;
import adlabs.nexus.client.backend.api.address.model.AddressTransaction;
import adlabs.nexus.client.backend.api.address.model.AssetBalance;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusAddressServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    @Test
    void getAddressInfo_maps() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        AddressInfo info = AddressInfo.builder()
                .address("addr1")
                .stakeAddress("stake1")
                .scriptAddress(true)
                .addressType("shelley")
                .balance("5000000")
                .assets(List.of(AssetBalance.builder().unit("policy1.asset1").quantity("10").build()))
                .build();
        when(sdkAddressSvc.getAddressInformation(eq(NET), eq("addr1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<AddressContent> r = svc.getAddressInfo("addr1");

        assertThat(r.isSuccessful()).isTrue();
        AddressContent ac = r.getValue();
        assertThat(ac.getStakeAddress()).isEqualTo("stake1");
        assertThat(ac.getScript()).isTrue();
        assertThat(ac.getType()).isEqualTo(AddressContent.TypeEnum.SHELLEY);
        assertThat(ac.getAmount()).extracting("unit", "quantity")
                .containsExactlyInAnyOrder(
                        tuple("lovelace", "5000000"),
                        tuple("policy1.asset1", "10"));
    }

    @Test
    void getAddressInfo_error_propagates() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressInformation(any(), any()))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<AddressContent> r = svc.getAddressInfo("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAddressInfo_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressInformation(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);

        assertThatThrownBy(() -> svc.getAddressInfo("addr1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getAddressDetails_unsupported() {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var svc = new NexusAddressService(sdkAddressSvc, NET);

        assertThatThrownBy(() -> svc.getAddressDetails("addr1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getTransactions_mapsAndCallsSdkWithPageAndPageSizeInOrder() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        AddressTransaction t1 = AddressTransaction.builder()
                .txHash("txh1").txIndex(0).blockHeight(100L).blockTime(1000L).build();
        AddressTransaction t2 = AddressTransaction.builder()
                .txHash("txh2").txIndex(1).blockHeight(101L).blockTime(1001L).build();
        when(sdkAddressSvc.getAddressTransactions(eq(NET), eq("addr1"), eq(2), eq(10)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(t1, t2)));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getTransactions("addr1", 10, 2);

        assertThat(r.isSuccessful()).isTrue();
        List<AddressTransactionContent> txs = r.getValue();
        assertThat(txs).hasSize(2);
        assertThat(txs.get(0).getTxHash()).isEqualTo("txh1");
        assertThat(txs.get(0).getTxIndex()).isEqualTo(0);
        assertThat(txs.get(0).getBlockHeight()).isEqualTo(100L);
        assertThat(txs.get(0).getBlockTime()).isEqualTo(1000L);
        verify(sdkAddressSvc, times(1)).getAddressTransactions(NET, "addr1", 2, 10);
    }

    @Test
    void getTransactions_withOrder_ignoresOrder() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        AddressTransaction t1 = AddressTransaction.builder()
                .txHash("txh1").txIndex(0).blockHeight(100L).blockTime(1000L).build();
        when(sdkAddressSvc.getAddressTransactions(eq(NET), eq("addr1"), eq(1), eq(5)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(t1)));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getTransactions("addr1", 5, 1, OrderEnum.desc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
    }

    @Test
    void getTransactions_error_propagates() throws Exception {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        when(sdkAddressSvc.getAddressTransactions(any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAddressService(sdkAddressSvc, NET);
        Result<List<AddressTransactionContent>> r = svc.getTransactions("addr1", 10, 1);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAllTransactions_unsupported() {
        var sdkAddressSvc = mock(adlabs.nexus.client.backend.api.address.AddressService.class);
        var svc = new NexusAddressService(sdkAddressSvc, NET);

        assertThatThrownBy(() -> svc.getAllTransactions("addr1", OrderEnum.asc, 0, 100))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
