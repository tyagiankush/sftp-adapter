# sftp-adapter
A file adapter that connects to a sftp server and performs functions

This a basic project that connects to a SFTP server and polls for *.json files. Once the files are fetched using "LS" command, the files are then splitted and passed on to a transformer to be transformed to a POJO interpretation of Request Entity class. You can add your implementation to a webservice call, and then the response file is sent to the server, using "PUT" command. Once the flow is complete, the request/input file is moved to an archive directory, using "MV" command. 

I used a SftpPersistentAcceptOnceFileListFilter, used to make sure we do not read the same file (name and timestamp) over and over again. This works till the metadata is not refreshed or the server is restarted.
