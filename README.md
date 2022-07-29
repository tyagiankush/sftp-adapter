# sftp-adapter
**Summary** - 
 - A file adapter that connects to a sftp server, polls for a file, reads the content of the file, transforms it to a POJO, calls a webservice, and outputs a response JSON file at the out directory.

**Functionality** - 
- This is a basic project that connects to a SFTP server and polls for *.json files. Once the files are fetched using "LS" command, the files are then splitted and passed on to a transformer to be transformed to a POJO interpretation of Request Entity class. You can add your implementation to a webservice call, and then the response file is sent to the server, using "PUT" command. Once the flow is complete, the request/input file is moved to an archive directory, using "MV" command. 

**_Note_**: 
I used a _SftpPersistentAcceptOnceFileListFilter_, used to make sure we do not read the same file (name and timestamp) over and over again. This works till the metadata is not refreshed or the server is restarted.

**File Description** - 
1. _IntegrationApplication.java_ - It is the starting point.
2. _InboundConfiguration.java_ - It contains the flow and channels.
3. _SftpTemplateConfiguration.java_ - It creates a DefaultSftpSessionFactory used to connecting to a SFTP server.
4. _Application.properties_ - It contains the SFTP server information needed for establishing a connection.
