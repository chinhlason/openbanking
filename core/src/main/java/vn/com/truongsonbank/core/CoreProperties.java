package vn.com.truongsonbank.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tsb.core")
public class CoreProperties {
    private String t29BaseUrl = "http://localhost:8087";

    public String getT29BaseUrl() {
        return t29BaseUrl;
    }

    public void setT29BaseUrl(String t29BaseUrl) {
        this.t29BaseUrl = t29BaseUrl;
    }
}
