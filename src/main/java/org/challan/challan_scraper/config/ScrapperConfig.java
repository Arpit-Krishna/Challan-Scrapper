package org.challan.challan_scraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.CookieManager;
import java.net.CookiePolicy;

@Configuration
public class ScrapperConfig {

    @Bean
    public CookieManager cookieManager() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return cookieManager;
    }
}