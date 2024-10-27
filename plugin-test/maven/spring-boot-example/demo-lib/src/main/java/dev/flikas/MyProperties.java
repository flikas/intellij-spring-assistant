package dev.flikas;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * This is my properties.
 */
@Component
@Data
@ConfigurationProperties("my")
public class MyProperties {
    /**
     *  Ù–‘1
     */
    private String p1;

    /**
     *  Ù–‘1
     */
    private String keyStore;

    /**
     * Jobs
     */
    private HashMap<String, Job> jobs;


    /**
     * keys
     */
    private HashSet<String> keys1;

    @Data
    public static class Job {
        /**
         * Job name
         */
        private String name;
        /**
         * Job cron
         */
        private String cron = "*";
    }
}
