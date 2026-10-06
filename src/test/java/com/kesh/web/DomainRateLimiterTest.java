package com.kesh.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.kesh.config.KeshProperties;

class DomainRateLimiterTest {

    @Test
    void secondRequestCanBeRejectedWhenBudgetIsZero() {
        KeshProperties properties = new KeshProperties();
        properties.getWeb().getRateLimit().setRequestsPerDomainPerMinute(1);
        DomainRateLimiter limiter = new DomainRateLimiter(properties);
        limiter.acquire("example.com", Duration.ZERO);
        assertThatThrownBy(() -> limiter.acquire("example.com", Duration.ZERO))
                .isInstanceOf(WebFetchException.class)
                .hasMessageContaining("Rate limit");
        assertThat(properties.getWeb().getRateLimit().getRequestsPerDomainPerMinute()).isEqualTo(1);
    }
}
