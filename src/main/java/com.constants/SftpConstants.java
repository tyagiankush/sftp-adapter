package com.constants;

public final class SftpConstants {

    public static final String GET_DIRECTORY = "*.json";
    public static final String SFTP_INBOUND_CHANNEL = "sftpInputChannel";
    public static final String STREAM_CHANNEL = "stream";
    public static final String JSON_TRANSFORMER_CHANNEL = "jsonTransformerChannel";
    public static final String WS_REQUEST_CHANNEL = "callWSChannel";
    public static final String WS_RESPONSE_CHANNEL = "wsResponseChannel";
    public static final String HEADER_FILE_TO_REMOVE = "fileToRemove";
    public static final String HEADER_FILE_TO_RENAME = "file_renameTo";
    public static final String HEADER_CURRENT_FILENAME = "current_FileName";
    public static final String HEADER_FILENAME_TIMESTAMP = "fileName_Timestamp";
    public static final String HEADER_START_TIME = "startTime";
    public static final String HEADER_END_TIME = "endTime";
    public static final String MAX_MSG_PER_POLL = "1";
    public static final String RESPONSE_PREFIX = "Response_";
    public static final String REQUEST_PREFIX = "Request_";
    public static final String JSON_FILE_EXT = ".json";
    public static final String COMMA = ",";
    public static final String UNDERSCORE = "_";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String EMPTY_STRING = "";
    public static final String SESSION_NOT_CREATED_MSG = "Failed to execute on session";
    public static final String FAILED_TO_CONNECT_MSG = "failed to connect";
    public static final String SFTP_SESSION_FAILED_MSG = "Creating SFTP session failed for SFTP Host: ";

    private SftpConstants() {
    }
}
