/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.oauth2.utils;

import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.context.base.BaseExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainRequest;
import io.gravitee.gateway.reactive.api.context.kafka.KafkaConnectionContext;
import java.util.List;
import java.util.Optional;
import javax.security.auth.callback.Callback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerExtensionsValidatorCallback;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerValidatorCallback;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenExtractor {

    static final String BEARER = "Bearer";
    static final String ACCESS_TOKEN = "access_token";

    /**
     * Extract an access token from a {@link BaseExecutionContext}.
     * If no access token has been found, an {@link Optional#empty()} is returned.
     *
     * @param ctx the execution context to extract the access token from.
     *
     * @return the access token as string, {@link Optional#empty()} if no token has been found.
     */
    public static Optional<String> extract(BaseExecutionContext ctx) {
        if (ctx instanceof HttpPlainExecutionContext httpPlainExecutionContext) {
            return extract(httpPlainExecutionContext.request());
        }
        KafkaConnectionContext kafkaConnectionContext = (KafkaConnectionContext) ctx;
        return extract(kafkaConnectionContext.callbacks());
    }

    /**
     * Extract an access token from a {@link javax.security.auth.callback.Callback} array.
     * If no access token has been found, an {@link Optional#empty()} is returned.
     *
     * @param callbacks the callbacks to extract the access token from.
     *
     * @return the access token as string, {@link Optional#empty()} if no token has been found.
     */
    private static Optional<String> extract(Callback[] callbacks) {
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerValidatorCallback oAuthCallback) {
                String token = oAuthCallback.tokenValue();
                return Optional.of(token);
            } else if (callback instanceof OAuthBearerExtensionsValidatorCallback oAuthCallback) {
                String token = oAuthCallback.token().value();
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    /**
     * Extract an access token from a {@link Request} headers or parameters.
     * <ul>
     *     <li>Get the first <code>Authorization</code> bearer header</li>
     *     <li>If no header, try to find an <code>access_token</code> query parameter</li>
     * </ul>
     *
     * If no access token has been found, an {@link Optional#empty()} is returned.
     *
     * @param request the request to extract the access token from.
     *
     * @return the access token as string, {@link Optional#empty()} if no token has been found.
     */
    private static Optional<String> extract(HttpPlainRequest request) {
        return extractFromHeaders(request.headers()).or(() -> extractFromParameters(request.parameters()));
    }

    /**
     * @deprecated kept for v3
     *
     * @param request the request to extract the JWT token from.
     * @return the access token as string or <code>null</code> if no token has been found.
     * @see #extract(HttpPlainRequest)
     */
    @Deprecated
    public static String extract(io.gravitee.gateway.api.Request request) {
        return extractFromHeaders(request.headers()).or(() -> extractFromParameters(request.parameters())).orElse(null);
    }

    private static Optional<String> extractFromHeaders(HttpHeaders headers) {
        if (headers != null) {
            List<String> authorizationHeaders = headers.getAll(HttpHeaderNames.AUTHORIZATION);

            if (!ObjectUtils.isEmpty(authorizationHeaders)) {
                Optional<String> authorizationBearerHeader = authorizationHeaders
                    .stream()
                    .filter(h -> StringUtils.startsWithIgnoreCase(h, BEARER))
                    .findFirst();

                if (authorizationBearerHeader.isPresent()) {
                    return Optional.of(authorizationBearerHeader.get().substring(BEARER.length()).trim());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> extractFromParameters(MultiValueMap<String, String> parameters) {
        if (parameters != null) {
            return Optional.ofNullable(parameters.getFirst(TokenExtractor.ACCESS_TOKEN));
        }

        return Optional.empty();
    }
}
