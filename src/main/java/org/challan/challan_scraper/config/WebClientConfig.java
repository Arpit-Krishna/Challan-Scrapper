package org.challan.challan_scraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public CookieManager cookieManager() {
        return new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    }

    @Bean
    public HttpClient httpClient(CookieManager cookieManager) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))   // connection timeout
                .cookieHandler(cookieManager)             // share cookies
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }
}
