package com.lovetropics.lib.backend;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record BackendConnectionConfig(
        URI uri,
        @Nullable String token,
        Set<String> subscriptions
) {
    public static BackendConnectionConfig of(URI uri) {
        return new BackendConnectionConfig(uri, null, Set.of());
    }

    public BackendConnectionConfig withToken(String token) {
        return new BackendConnectionConfig(uri, token, subscriptions);
    }

    public BackendConnectionConfig withSubscriptions(String... subscriptions) {
        return withSubscriptions(Arrays.asList(subscriptions));
    }

    public BackendConnectionConfig withSubscriptions(Collection<String> subscriptions) {
        return new BackendConnectionConfig(uri, token, ImmutableSet.<String>builder()
                .addAll(this.subscriptions)
                .addAll(subscriptions)
                .build()
        );
    }

    public URI decoratedUri() {
        if (token == null && subscriptions.isEmpty()) {
            return uri;
        }

        List<Pair<String, String>> parameters = new ArrayList<>();
        if (token != null) {
            parameters.add(Pair.of("token", token));
        }
        for (String subscription : subscriptions) {
            parameters.add(Pair.of("sub", subscription));
        }
        return appendQueryParameters(uri, parameters);
    }

    private static URI appendQueryParameters(URI uri, List<Pair<String, String>> parameters) {
        StringBuilder query = new StringBuilder(Objects.requireNonNullElse(uri.getQuery(), ""));
        for (Pair<String, String> parameter : parameters) {
            if (!query.isEmpty()) {
                query.append("&");
            }
            query.append(parameter.getFirst()).append("=").append(URLEncoder.encode(parameter.getSecond(), StandardCharsets.UTF_8));
        }

        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), query.toString(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
