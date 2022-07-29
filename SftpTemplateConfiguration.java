package com.sftpadapter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;

@Configuration
@Qualifier("template")
class SftpTemplateConfiguration {
    private static final Logger logger = LogManager.getLogger(SftpTemplateConfiguration.class);

    @Bean
    @Qualifier("defaultSftpSessionFactory")
    DefaultSftpSessionFactory defaultSftpSessionFactory(
            @Value("${FTP.USERNAME}") String username,
            @Value("${FTP.PASSWORD}") String pw,
            @Value("${FTP.HOST}") String host,
            @Value("${FTP.ENV}") String env,
            @Value("${FTP.SOURCE}") String basePath,
            @Value("${FTP.PORT}") int port) {

        logger.info("Creating session factory for user - " + username);
        logger.info("Env Name: " + env);
        try {
            DefaultSftpSessionFactory defaultSftpSessionFactory = new DefaultSftpSessionFactory();
            defaultSftpSessionFactory.setPassword(pw);
            defaultSftpSessionFactory.setUser(username);
            if (basePath == null || basePath.equals("")) {
                throw new IllegalArgumentException("Base Path missing...");
            }
            defaultSftpSessionFactory.setAllowUnknownKeys(true);
            defaultSftpSessionFactory.setHost(host);
            defaultSftpSessionFactory.setPort(port);
            return defaultSftpSessionFactory;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Bean
    @Qualifier("sftpRemoteFileTemplate")
    SftpRemoteFileTemplate sftpRemoteFileTemplate(DefaultSftpSessionFactory dsf) {
        logger.info("SFTP remote file template...");
        return new SftpRemoteFileTemplate(dsf);
    }
}
