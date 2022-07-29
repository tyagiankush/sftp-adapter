package com.sftpadapter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@PropertySource("classpath:application.properties")
@RestController
public class IntegrationApplication {
    private static final Logger logger = LogManager.getLogger(IntegrationApplication.class);

    public static void main(String[] args) {
        logger.info("Profile activated: " + System.getProperty("spring.profiles.active"));
        SpringApplication.run(IntegrationApplication.class);
    }

    @RequestMapping(value = "/")
    public String home() {
        try {
            return "SFTP Adapter is up and running on the machine - " + InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "SFTP Adapter is up and running... ";
        }
    }
}
