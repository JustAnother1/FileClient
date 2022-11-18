package de.nomagic;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class FileRequest
{
    public final static int NOT_IDENTICAL = 1;
    public final static int OK = 0;
    public final static int ERROR_CONNECTION_SERVER_FAILED = -1;
    public final static int ERROR_SERVER_NO_FILE_LENGTH = -2;
    public final static int ERROR_LOCAL_READ_FAILED = -3;
    public final static int ERROR_SERVER_INVALID_REPLY = -4;
    public final static int ERROR_USER_INVALID_COMMAND = -5;
    public final static int ERR_REPORTED_SERVER = -10; // must always be lowest value

    private Socket clientSocket;
    private InputStream inFromServer;
    private DataOutputStream outToServer;

    private String serverURL;
    private int serverPort;
    private String clientId;

    private InputStream localFileRead = null;
    private long localFileLength = 0;
    private OutputStream localFileWrite = null;

    public FileRequest(String serverURL, int serverPort, String clientId)
    {
        this.serverURL = serverURL;
        this.serverPort = serverPort;
        this.clientId = clientId;
    }

    public void addLocalFileForReading(InputStream fin, long length)
    {
        localFileRead = fin;
        localFileLength = length;
    }

    public void addLocalFileForWriting(OutputStream fout)
    {
        localFileWrite = fout;
    }

    public int makeRequest(String command, String remoteFileName)
    {
        if(false == connectTo(serverURL, serverPort))
        {
            System.err.println("Could not connect to Server !");
            return ERROR_CONNECTION_SERVER_FAILED;
        }
        int res = executeCommand(command, remoteFileName);
        disconnect();
        return res;
    }

    private boolean connectTo(String serverURL, int serverPort)
    {
        try
        {
            clientSocket = new Socket(serverURL, serverPort);
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = clientSocket.getInputStream();
            return true;
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return false;
    }

    private void disconnect()
    {
        try
        {
            clientSocket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private int sendRequest(String command, String remoteFileName, boolean sendFile) throws IOException
    {
        // construct request
        StringBuffer RequestHeader = new StringBuffer();
        RequestHeader.append("2:");
        if("compare".equals(command))
        {
            RequestHeader.append("get:");
        }
        else
        {
            RequestHeader.append(command + ":");
        }
        if(null != clientId)
        {
            RequestHeader.append("name=" + clientId + ":");
        }
        RequestHeader.append("file=" + remoteFileName + ":");
        if(true == sendFile)
        {
            RequestHeader.append("fileContentLength=" + localFileLength + ":");
        }
        String requestHeaderString = RequestHeader.toString() + "\n";
        System.out.println("Sending Request: " + requestHeaderString);
        byte[] b = requestHeaderString.getBytes(StandardCharsets.UTF_8);
        outToServer.write(b, 0, b.length);
        // send file data if we have to
        if(true == sendFile)
        {
            byte[] buf = new byte[4096];
            int num;
            do {
                num = localFileRead.read(buf);
                if(num > 0)
                {
                    outToServer.write(buf, 0, num);
                }
            }while(num > 0);
            byte[] end = "2:\n".getBytes(StandardCharsets.UTF_8);
            outToServer.write(end, 0, end.length);
        }
        outToServer.flush();
        return OK;
    }

    private int handleReceiveFile(String[] sections, String command) throws IOException
    {
        boolean identical = true;
        // get file length
        long lengthOfFile = 0;
        long lengthToCompare = 0;
        for(int i = 0; i < sections.length; i++)
        {
            if(true == sections[i].startsWith("fileContentLength"))
            {
                String fs = sections[i].substring(sections[i].indexOf('=') + 1);
                fs = fs.trim();
                lengthOfFile = Long.parseLong(fs);
            }
        }
        if(0 == lengthOfFile)
        {
            System.err.println("ERROR: no file length provided by server !");
            return ERROR_SERVER_NO_FILE_LENGTH;
        }

        if("compare".equals(command))
        {
            if(lengthOfFile != localFileLength)
            {
                System.err.println("COMAPRE: different file sizes !(local: " + localFileLength + ", remote: " + lengthOfFile + ")");
                identical = false;
            }

            byte[] remoteBuf = new byte[4096];
            byte[] localBuf = new byte[4096];
            boolean reported = false;
            int num;
            if(localFileLength < lengthOfFile)
            {
                lengthToCompare = localFileLength;
            }
            else
            {
                lengthToCompare = lengthOfFile;
            }
            long stillToCompare = lengthToCompare;
            do {
                int maxLength = 4096;
                if(stillToCompare < maxLength)
                {
                    maxLength = (int)stillToCompare;
                }
                num = inFromServer.read(remoteBuf, 0, maxLength);
                if(num > 0)
                {
                    if(num != localFileRead.read(localBuf, 0, num))
                    {
                        System.err.println("ERROR: local file can not be read!");
                        return ERROR_LOCAL_READ_FAILED;
                    }
                    for(int i = 0; i < num; i++)
                    {
                        if(localBuf[i] != remoteBuf[i])
                        {
                            if(false == reported)
                            {
                                System.err.println("COMAPRE: different file content !");
                                reported = true;
                            }
                            identical = false;
                            break;
                        }
                    }
                    stillToCompare = stillToCompare - num;
                }
            }while(num > 0);
            // if local file was shorter read the rest of the remote file
            lengthOfFile = lengthOfFile - lengthToCompare;
            if(0 < lengthOfFile)
            {
                do {
                    int maxLength = 4096;
                    if(lengthOfFile < maxLength)
                    {
                        maxLength = (int)lengthOfFile;
                    }
                    num = inFromServer.read(remoteBuf, 0, maxLength);
                    if(num > 0)
                    {
                        lengthOfFile = lengthOfFile - num;
                    }
                }while(num > 0);
            }
        }
        else
        {
            // save the file locally
            byte[] buf = new byte[4096];
            int num;
            do {
                int maxLength = 4096;
                if(lengthOfFile < maxLength)
                {
                    maxLength = (int)lengthOfFile;
                }
                num = inFromServer.read(buf, 0, maxLength);
                // System.out.println("bytes to go: " + lengthOfFile + ", max chunk : "  + maxLength + ", actually read: "  + num);
                if(0 < num)
                {
                    localFileWrite.write(buf, 0, num);
                    lengthOfFile = lengthOfFile - num;
                }
            }while((num > 0) && (lengthOfFile > 0));
        }

        // read end of reply
        int b = inFromServer.read();
        if(-1 == b)
        {
            // no more bytes in the stream
            System.err.println("ERROR: reply too short !");
            return ERROR_SERVER_INVALID_REPLY;
        }
        if('2' != b)
        {
            System.err.println("ERROR: reply invalid (4) : " + (char) b + " (" + b + ") !");
            b = inFromServer.read();
            System.err.println(" " + (char) b + " (" + b + ") !");
            b = inFromServer.read();
            System.err.println(" " + (char) b + " (" + b + ") !");
            b = inFromServer.read();
            System.err.println(" " + (char) b + " (" + b + ") !");
            return ERROR_SERVER_INVALID_REPLY;
        }
        b = inFromServer.read();
        if(-1 == b)
        {
            // no more bytes in the stream
            System.err.println("ERROR: reply too short !");
            return ERROR_SERVER_INVALID_REPLY;
        }
        if(':' != b)
        {
            System.err.println("ERROR: reply invalid (5)!");
            return ERROR_SERVER_INVALID_REPLY;
        }
        b = inFromServer.read();
        if(-1 == b)
        {
            // no more bytes in the stream
            System.err.println("ERROR: reply too short !");
            return ERROR_SERVER_INVALID_REPLY;
        }
        if('\n' != b)
        {
            System.err.println("ERROR: reply invalid (6)!");
            return ERROR_SERVER_INVALID_REPLY;
        }
        if("compare".equals(command))
        {
            if(true == identical)
            {
                return OK;
            }
            else
            {
                return NOT_IDENTICAL;
            }
        }
        return OK;
    }

    private int executeCommand(String command, String remoteFileName)
    {
        boolean sendFile = false;
        boolean receiveFile = false;
        switch(command)
        {
        case "store":
            sendFile = true;
            break;

        case "update":
            sendFile = true;
            break;

        case "get":
            receiveFile = true;
            break;

        case "has":
            break;

        case "compare":
            receiveFile = true;
            break;

        default:
            System.err.println("ERROR: invalid command!");
            return ERROR_USER_INVALID_COMMAND;
        }
        try
        {
            int res = sendRequest(command, remoteFileName, sendFile);
            if(0 != res)
            {
                // sending the request did not work
                return res;
            }
            // Request has been send to the server

            // Parse reply
            int b = inFromServer.read();
            if(-1 == b)
            {
                // no more bytes in the stream
                System.err.println("ERROR: reply too short !");
                return ERROR_SERVER_INVALID_REPLY;
            }
            if('2' != b)
            {
                System.err.println("ERROR: reply invalid (1)!");
                return ERROR_SERVER_INVALID_REPLY;
            }
            b = inFromServer.read();
            if(-1 == b)
            {
                // no more bytes in the stream
                System.err.println("ERROR: reply too short !");
                return ERROR_SERVER_INVALID_REPLY;
            }
            if(':' != b)
            {
                System.err.println("ERROR: reply invalid (2)!");
                return ERROR_SERVER_INVALID_REPLY;
            }
            b = inFromServer.read();
            if(-1 == b)
            {
                // no more bytes in the stream
                System.err.println("ERROR: reply too short !");
                return ERROR_SERVER_INVALID_REPLY;
            }
            if('0' != b)
            {
                System.out.println("Error state " + (char)b + " received from Server!");
                int err = b;
                StringBuffer Reason = new StringBuffer();
                try
                {
                    do {
                        b = inFromServer.read();
                        Reason.append((char)b);
                    }while(('\n' != b) && (-1 != b));
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    System.err.println("ERROR: IOException !");
                }
                System.out.println("Reason: " + Reason.toString());
                return (Math.abs(err) * -1) + ERR_REPORTED_SERVER;
            }
            b = inFromServer.read();
            if(-1 == b)
            {
                // no more bytes in the stream
                System.err.println("ERROR: reply too short !");
                return ERROR_SERVER_INVALID_REPLY;
            }
            if(':' != b)
            {
                System.err.println("ERROR: reply invalid (3)!");
                return ERROR_SERVER_INVALID_REPLY;
            }
            StringBuffer DataSections = new StringBuffer();
            do {
                b = inFromServer.read();
                DataSections.append((char)b);
            }while(('\n' != b) && (-1 != b));
            String[] sections = DataSections.toString().split(":");
            if(true == receiveFile)
            {
                res = handleReceiveFile(sections, command);
            }
            return res;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.err.println("ERROR: IOException !");
        }
        System.err.println("ERROR: reply invalid (7)!");
        return ERROR_SERVER_INVALID_REPLY;
    }

}
