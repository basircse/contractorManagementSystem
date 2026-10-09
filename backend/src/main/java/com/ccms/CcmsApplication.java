package com.ccms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CcmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CcmsApplication.class, args);
    }
}
