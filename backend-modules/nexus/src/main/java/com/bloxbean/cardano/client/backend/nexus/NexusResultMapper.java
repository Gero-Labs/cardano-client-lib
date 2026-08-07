package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.Result;

import java.util.function.Function;

public final class NexusResultMapper {
    private NexusResultMapper() {
    }

    // Result's static factories are raw (not method-generic); the returned Result<B> assignment is an unchecked widening, mirroring koios's usage pattern.
    public static <S, B> Result<B> map(adlabs.nexus.client.backend.api.base.Result<S> res, Function<S, B> conv) {
        if (!res.isSuccessful()) {
            return Result.error(res.getResponse()).code(res.getCode());
        }
        return Result.success("OK").withValue(conv.apply(res.getValue())).code(200);
    }
}
