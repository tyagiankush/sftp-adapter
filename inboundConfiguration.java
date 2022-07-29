package com.sftpadapter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.Poller;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.*;
import org.springframework.integration.file.filters.AbstractFileListFilter;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.file.support.FileExistsMode;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.integration.sftp.dsl.SftpOutboundGatewaySpec;
import org.springframework.integration.sftp.filters.SftpPersistentAcceptOnceFileListFilter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.support.json.Jackson2JsonObjectMapper;
import org.springframework.integration.support.json.JsonObjectMapper;
import org.springframework.integration.transformer.StreamTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.PollableChannel;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
@Qualifier("inbound")
class InboundConfiguration {
    private static final Logger LOGGER = LogManager.getLogger(InboundConfiguration.class);
    @Autowired
    @Qualifier("webserviceTypeFactory")
    WebserviceTypeFactory webserviceTypeFactory;

    @Value("${POLLER.DELAY}")
    String POLLER_VALUE_IN_MILLIS;
    @Value("${FTP.SOURCE}")
    String BASE_REMOTE_FOLDER_TO_GET_FILES_FROM;
    @Value("${FTP.HOST}")
    String sftpHost;
  
    String REMOTE_OUT_FOLDER_PATH_AS_EXPRESSION;
    String REMOTE_REPORT_FOLDER_PATH_AS_EXPRESSION;
    String REMOTE_IN_FOLDER_PATH_AS_EXPRESSION;
    String REMOTE_ARCHIVE_FOLDER_PATH;
    String REMOTE_ARCHIVE_PATH_AS_EXPR;

    @Bean
    public void instantiatePaths() {
        REMOTE_OUT_FOLDER_PATH_AS_EXPRESSION = "'" + BASE_REMOTE_FOLDER_TO_GET_FILES_FROM + "out/" + "'";
        REMOTE_REPORT_FOLDER_PATH_AS_EXPRESSION = "'" + BASE_REMOTE_FOLDER_TO_GET_FILES_FROM + "report/" + "'";
        REMOTE_IN_FOLDER_PATH_AS_EXPRESSION = "'" + BASE_REMOTE_FOLDER_TO_GET_FILES_FROM + "in/" + "'";
        REMOTE_ARCHIVE_FOLDER_PATH = BASE_REMOTE_FOLDER_TO_GET_FILES_FROM + "in/archive/";
        REMOTE_ARCHIVE_PATH_AS_EXPR = "'" + REMOTE_ARCHIVE_FOLDER_PATH + "Request_'";
    }

    @InboundChannelAdapter(value = SFTP_INBOUND_CHANNEL,
            poller = @Poller(fixedDelay = "${POLLER.DELAY}", maxMessagesPerPoll = MAX_MSG_PER_POLL))
    public String filesForSftpGet() {
        return GET_DIRECTORY;
    }

    @Bean
    @Qualifier("inboundFlow")
    IntegrationFlow inboundFlow(SessionFactory<LsEntry> isf) {
        LOGGER.info("Setting up inbound channel...");
        return IntegrationFlows
                .from(SFTP_INBOUND_CHANNEL)
                .log(logForNextPoll())
                .handle(listRemoteFiles(isf))
                //.handle(listRemoteFilesOnlyNames(isf))
                .log(getPayload())
                .split().channel(MessageChannels.queue())
                .enrichHeaders(addStartTimeToMessageHeader())
                .log(logTheFilePath())
                .enrichHeaders(addAMessageHeader())
                //.enrichHeaders(addARenameMessageHeader())
                .enrichHeaders(addFileNameToMessageHeader())
                .enrichHeaders(addUniqueIdPerMsgToMessageHeader())
                .log(logTheMessageHeaders())
                .log(getPayload())
                .handle(getTheFile(isf))
                //.log(logTheFileContent())
                .channel(STREAM_CHANNEL)
                .get();
    }

    @Bean
    @Qualifier("streamToStringFlow")
    @Transformer(inputChannel = STREAM_CHANNEL, outputChannel = JSON_TRANSFORMER_CHANNEL)
    public StreamTransformer streamToString() {
        return new StreamTransformer("UTF-8"); // transforms to String
    }

    @Bean
    @Qualifier("transformerFlow")
    IntegrationFlow transformerFlow(SessionFactory<LsEntry> dsf) {
        LOGGER.info("Setting up transformer inbound channel...");
        return IntegrationFlows
                .from(JSON_TRANSFORMER_CHANNEL)
                .log(m -> "### Transforming... " + m.getHeaders().get(HEADER_CURRENT_FILENAME))
                .transform(Transformers.fromJson(RequestEntity.class, jsonObjectMapper()))
                .channel(WS_REQUEST_CHANNEL)
                .get();
    }

    @Bean
    @Qualifier("callWSFlow")
    IntegrationFlow callWSFlow(SessionFactory<LsEntry> tsf) {
        LOGGER.info("Setting up callWSFlow outbound channel...");
        return IntegrationFlows
                .from(WS_REQUEST_CHANNEL)
                .enrichHeaders(h -> h.headerExpression(HEADER_WS_START_TIME, " + headers['timestamp']"))
                .handle(this::invokeService)
                .enrichHeaders(h -> h.headerExpression(HEADER_WS_END_TIME, " + headers['timestamp']"))
                .log(getPayload())
                .channel(WS_RESPONSE_CHANNEL)
                .get();
    }

    @Bean
    @Qualifier("writeWSResponseFlow")
    IntegrationFlow writeWSResponseFlow(SessionFactory<LsEntry> trsf) {
        LOGGER.info("Setting up WS response outbound channel...");
        return IntegrationFlows
                .from(WS_RESPONSE_CHANNEL)
                .log(logTheMessageHeaders())
                .handle(putTheFile(trsf))
                .log(getPayload())
                .handle(moveFileToArchive(trsf))
                .log(getPayload())
                .channel(MessageChannels.queue())
                .enrichHeaders(addEndTimeToMessageHeader())
                .channel(MessageChannels.queue())
                .get();
    }

    @Bean
    @Qualifier("errorHandlerFlow")
    IntegrationFlow errorHandlerFlow(SessionFactory<LsEntry> esf) {
        LOGGER.info("Setting up Error handler channel...");
        return IntegrationFlows
                .from(errorChannel())
                .log(logTheMessageHeaders())
                .handle(this::buildHeadersFromFailedMsgHeaders)
                .log(getPayload())
                .enrichHeaders(addFileStatusToMessageHeaders())
                .log(logTheMessageHeaders())
                .handle(this::createErrorResponse)
                .log(getPayload())
                .route(String.class, p -> p.contains(SESSION_NOT_CREATED_MSG) || p.contains(FAILED_TO_CONNECT_MSG)
                        ? "nullChannel" : WS_RESPONSE_CHANNEL)
                .get();
    }

    @Bean
    public PollableChannel errorChannel() {
        return new QueueChannel();
    }

    @Bean
    public JsonObjectMapper<JsonNode, JsonParser> jsonObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonObjectMapper(mapper);
    }

    public String invokeService(RequestEntity payload, Map<String, Object> headers) {
        try {
            LOGGER.info("Starting WS flow...");
            // call service here
        } catch (JsonProcessingException) {
            LOGGER.error("Exception occurred while processing request: " + payload, e);
            return "error";
        } catch (Exception e) {
            LOGGER.error("Exception occurred, Please check logs. Payload: " + payload, e);
            return "error";
        }
    }

    private Message<Object> buildHeadersFromFailedMsgHeaders(Object payload, Map<String, Object> headers) {
        return MessageBuilder.withPayload(payload).copyHeaders(headers).copyHeadersIfAbsent(
                Objects.requireNonNull(((MessagingException) payload).getFailedMessage()).getHeaders()).build();
    }

    public String createErrorResponse(Object payload, Map<String, Object> headers) {

       // handle error flow
        return "error";
    }

    /* Persistent file list filter using the server's file timestamp to detect if we've already 'seen' this file.
        Without it, the program would report the same file over and over again. */
    private SftpPersistentAcceptOnceFileListFilter onlyFilesWeHaveNotSeenYet() {
        return new SftpPersistentAcceptOnceFileListFilter(new SimpleMetadataStore(), SFTP_INBOUND_CHANNEL);
    }

    private AbstractFileListFilter<LsEntry> onlyJsonFiles() {
        return new AbstractFileListFilter<>() {
            @Override
            public boolean accept(ChannelSftp.LsEntry file) {
                return file.getFilename().endsWith(JSON_FILE_EXT);
            }
        };
    }

    private Function<Message<Object>, Object> logArchivePath() {
        return message -> "### Archive path: " + REMOTE_ARCHIVE_PATH_AS_EXPR;
    }

    private Function<Message<Object>, Object> logForNextPoll() {
        return message -> "### Polling for files every - " + Integer.parseInt(POLLER_VALUE_IN_MILLIS) / 1000 + " Seconds... ###";
    }

    private Function<Message<Object>, Object> logTheFilePath() {
        return message -> "### File path: " + message.getPayload();
    }

    private Function<Message<Object>, Object> getPayload() {
        return message -> "### Response: " + message.getPayload();
    }

    private Consumer<HeaderEnricherSpec> addAMessageHeader() {
        return headers -> headers.headerExpression(HEADER_FILE_TO_REMOVE,
                REMOTE_IN_FOLDER_PATH_AS_EXPRESSION + " + payload");
    }

    private Consumer<HeaderEnricherSpec> addStartTimeToMessageHeader() {
        return headers -> headers.headerExpression(HEADER_START_TIME, " + headers['timestamp']");
    }

    private Consumer<HeaderEnricherSpec> addEndTimeToMessageHeader() {
        return headers -> headers.headerExpression(HEADER_END_TIME, " + headers['timestamp']");
    }

    private Consumer<HeaderEnricherSpec> addUniqueIdPerMsgToMessageHeader() {
        return headers -> headers.headerExpression(HEADER_FILENAME_TIMESTAMP, " + headers['timestamp']");
    }

    private Consumer<HeaderEnricherSpec> addFileStatusToMessageHeaders() {
        return headers -> headers.headerExpression(HEADER_FILE_STATUS, "'Failed'");
    }

    private Consumer<HeaderEnricherSpec> addARenameMessageHeader() {
        return headers -> headers.headerExpression(HEADER_FILE_TO_RENAME, REMOTE_ARCHIVE_PATH_AS_EXPR + " + payload");
    }

    private Consumer<HeaderEnricherSpec> addFileNameToMessageHeader() {
        return headers -> headers.headerExpression(HEADER_CURRENT_FILENAME, "''" + " + payload");
    }

    private Function<Message<Object>, Object> logTheMessageHeaders() {
        return message -> "### Header file info: " + message.getHeaders();
    }

    private Function<Message<Object>, Object> logTheFileContent() {
        return message -> "### File content line: '" + message.getPayload() + "'";
    }

    private SftpOutboundGatewaySpec listRemoteFilesOnlyNames(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory,
                        AbstractRemoteFileOutboundGateway.Command.NLST, REMOTE_IN_FOLDER_PATH_AS_EXPRESSION)
                .filter(onlyJsonFiles());
    }

    private SftpOutboundGatewaySpec getTheFile(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory, AbstractRemoteFileOutboundGateway.Command.GET,
                        "headers['file_remoteDirectory'] + payload")
                .options(AbstractRemoteFileOutboundGateway.Option.EXCEPTION_WHEN_EMPTY)
                .options(AbstractRemoteFileOutboundGateway.Option.PRESERVE_TIMESTAMP)
                .options(AbstractRemoteFileOutboundGateway.Option.STREAM);
    }

    private SftpOutboundGatewaySpec putTheFile(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory, AbstractRemoteFileOutboundGateway.Command.PUT, "payload")
                .remoteDirectoryExpression(REMOTE_OUT_FOLDER_PATH_AS_EXPRESSION)
                .fileExistsMode(FileExistsMode.REPLACE)
                .fileNameGenerator(message -> RESPONSE_PREFIX + message.getHeaders().get(HEADER_FILENAME_TIMESTAMP)
                        + UNDERSCORE + message.getHeaders().get(HEADER_CURRENT_FILENAME));
    }

    private SftpOutboundGatewaySpec putTheReportFile(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory, AbstractRemoteFileOutboundGateway.Command.PUT, "payload")
                .remoteDirectoryExpression(REMOTE_REPORT_FOLDER_PATH_AS_EXPRESSION)
                .fileExistsMode(FileExistsMode.IGNORE)
                .fileNameGenerator(
                        message -> REPORT_PREFIX + message.getHeaders().get(HEADER_FILENAME_TIMESTAMP) + UNDERSCORE
                                + Objects.requireNonNull(message.getHeaders().get(HEADER_CURRENT_FILENAME))
                                .toString().replace(JSON_FILE_EXT, REPORT_EXT));
    }

    private SftpOutboundGatewaySpec moveFileToArchive(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory, AbstractRemoteFileOutboundGateway.Command.MV,
                        REMOTE_IN_FOLDER_PATH_AS_EXPRESSION + " + headers['current_FileName']")
                .fileExistsMode(FileExistsMode.REPLACE)
                .renameExpression(REMOTE_ARCHIVE_PATH_AS_EXPR + " + headers['fileName_Timestamp'] + '_' + headers['current_FileName']");
    }

    private SftpOutboundGatewaySpec listRemoteFiles(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory,
                        AbstractRemoteFileOutboundGateway.Command.LS, REMOTE_IN_FOLDER_PATH_AS_EXPRESSION)
                .options(AbstractRemoteFileOutboundGateway.Option.NAME_ONLY)
                .filter(onlyFilesWeHaveNotSeenYet())
                .filter(onlyJsonFiles());
    }

    private SftpOutboundGatewaySpec getTheFiles(SessionFactory<ChannelSftp.LsEntry> sessionFactory) {
        return Sftp.outboundGateway(sessionFactory, AbstractRemoteFileOutboundGateway.Command.MGET,
                        REMOTE_IN_FOLDER_PATH_AS_EXPRESSION)
                .localDirectoryExpression("'myDir/' + #remoteDirectory")
                .localFilenameExpression("#remoteFileName.replaceFirst('sftpSource', 'localTarget')")
                .autoCreateLocalDirectory(true)
                .regexFileNameFilter("^.*.json$")
                .fileExistsMode(FileExistsMode.REPLACE)
                .options(AbstractRemoteFileOutboundGateway.Option.STREAM)
                .options(AbstractRemoteFileOutboundGateway.Option.EXCEPTION_WHEN_EMPTY)
                .options(AbstractRemoteFileOutboundGateway.Option.PRESERVE_TIMESTAMP);
    }

    @Bean
    public QueueChannelSpec remoteFileOutputChannel() {
        return MessageChannels.queue();
    }

}
