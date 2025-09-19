package org.challan.challan_scraper.utills;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OkHttpUtils {

    private static final OkHttpClient defaultClient = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    public static OkHttpClient getOkHttpClient(int timeoutMs) {
        return defaultClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }
}
