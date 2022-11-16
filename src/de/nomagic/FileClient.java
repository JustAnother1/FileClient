package de.nomagic;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;


public class FileClient
{
    private int ServerPort = 4321;
    private String ServerURL = "127.0.0.1";
    private String ClientId = null;
    private String localFileName = "";
    private String remoteFileName = "";
    private String command = "";
    private boolean isConnected = false;
    private boolean sendFile = false;
    private boolean receiveFile = false;
    private Socket clientSocket;
    private InputStream inFromServer;
    private DataOutputStream outToServer;
    private File LocalFile = null;

    public FileClient()
    {
    }

    public static void main(String[] args)
    {
        FileClient m = new FileClient();
        m.getConfigFromCommandLine(args);
        int res = m.doTheWork();
        if(0 > res)
        {
            System.out.println("Failed !");
        }
        System.exit(res);
    }

    private void printHelpText()
    {
        System.err.println("Parameters:");
        System.err.println("===========");
        System.err.println("-h");
        System.err.println("     : This text");
        System.err.println("-store <file name> <destination path and name>");
        System.err.println("     : copy the local file to the server");
        System.err.println("-update <file name> <destination path and name>");
        System.err.println("     : replace the file on the server with this newer one");
        System.err.println("-get <file name>");
        System.err.println("     : retrieve the file from the server");
        System.err.println("-has <file name>");
        System.err.println("     : check if the server has a file with that name.");
        System.err.println("-host hostname");
        System.err.println("     : connect to the remote server on the host 'hostname'");
        System.err.println("-port <port number>");
        System.err.println("     : use the given port instead of the default port " + ServerPort);
        System.err.println("-requestId clientName");
        System.err.println("     : send 'clientName' to the server as identification.");
        System.err.println("-compare <local file name> <stored file name>");
        System.err.println("     : test if the local and stored files are identical");
    }

    public void getConfigFromCommandLine(String[] args)
    {
        for(int i = 0; i < args.length; i++)
        {
            if(true == args[i].startsWith("-"))
            {
                if(true == "-store".equals(args[i]))
                {
                    command = "store";
                    i++;
                    localFileName = args[i];
                    i++;
                    remoteFileName = args[i];
                    sendFile = true;
                }
                else if(true == "-update".equals(args[i]))
                {
                    command = "update";
                    i++;
                    localFileName = args[i];
                    i++;
                    remoteFileName = args[i];
                    sendFile = true;
                }
                else if(true == "-get".equals(args[i]))
                {
                    command = "get";
                    i++;
                    remoteFileName = args[i];
                    receiveFile = true;
                }
                else if(true == "-has".equals(args[i]))
                {
                    command = "has";
                    i++;
                    remoteFileName = args[i];
                }
                else if(true == "-host".equals(args[i]))
                {
                    i++;
                    ServerURL = args[i];
                }
                else if(true == "-port".equals(args[i]))
                {
                    i++;
                    ServerPort = Integer.parseInt(args[i]);
                }
                else if(true == "-requestId".equals(args[i]))
                {
                    i++;
                    ClientId = args[i];
                }
                else if(true == "-h".equals(args[i]))
                {
                    printHelpText();
                    System.exit(0);
                }
                else if(true == "-compare".equals(args[i]))
                {
                    command = "compare";
                    i++;
                    localFileName = args[i];
                    i++;
                    remoteFileName = args[i];
                    receiveFile = true;
                }
                else
                {
                    System.err.println("Invalid Parameter : " + args[i]);
                    printHelpText();
                    System.exit(-2);
                }
            }
            else
            {
                System.err.println("Invalid Parameter : " + args[i]);
                printHelpText();
                System.exit(-1);
            }
        }
    }

    public int doTheWork()
    {
        if("".equals(command))
        {
            printHelpText();
            System.exit(-1);
        }
        // Connect to server
        connectTo(ServerURL, ServerPort);
        if(false == isConnected)
        {
            System.err.println("Could not connect to Server !");
            System.exit(-1);
        }
        int res = executeCommand();
        disconnect();
        return res;
    }

    private int sendRequest() throws IOException
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
        if(null != ClientId)
        {
            RequestHeader.append("name=" + ClientId + ":");
        }
        RequestHeader.append("file=" + remoteFileName + ":");
        if(true == sendFile)
        {
            LocalFile = new File(localFileName);
            if(false == LocalFile.exists())
            {
                System.err.println("ERROR: file that shall be send does not exist!");
                return -2;
            }
            if(false == LocalFile.canRead())
            {
                System.err.println("ERROR: file that shall be send can not be read!");
                return -3;
            }
            RequestHeader.append("fileContentLength=" + LocalFile.length() + ":");
        }
        String requestHeaderString = RequestHeader.toString() + "\n";
        System.out.println("Sending Request: " + requestHeaderString);
        outToServer.writeBytes(requestHeaderString);
        // send file data if we have to
        if(true == sendFile)
        {
            byte[] buf = new byte[4096];
            FileInputStream fin = new FileInputStream(LocalFile);
            int num;
            do {
                num = fin.read(buf);
                if(num > 0)
                {
                    outToServer.write(buf, 0, num);
                }
            }while(num > 0);
            fin.close();
            outToServer.writeBytes("2:\n");
        }
        outToServer.flush();
        return 0;
    }

    private int handleReceiveFile(String[] sections) throws IOException
    {
        boolean identical = true;
        // get file length
        long lengthOfFile = 0;
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
            return -12;
        }

        if("compare".equals(command))
        {
            LocalFile = new File(localFileName);
            if(false == LocalFile.exists())
            {
                System.err.println("ERROR: local file does not exist!");
                return -2;
            }
            if(false == LocalFile.canRead())
            {
                System.err.println("ERROR: local file can not be read!");
                return -3;
            }
            if(lengthOfFile != LocalFile.length())
            {
                System.err.println("COMAPRE: different file sizes !");
                identical = false;
            }

            byte[] remoteBuf = new byte[4096];
            byte[] localBuf = new byte[4096];
            boolean reported = false;
            FileInputStream fin = new FileInputStream(LocalFile);
            int num;
            do {
                int maxLength = 4096;
                if(lengthOfFile < maxLength)
                {
                    maxLength = (int)lengthOfFile;
                }
                num = inFromServer.read(remoteBuf, 0, maxLength);
                if(num > 0)
                {
                    if(num != fin.read(localBuf, 0, num))
                    {
                        System.err.println("ERROR: local file can not be read!");
                        fin.close();
                        return -3;
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
                    lengthOfFile = lengthOfFile - num;
                }
            }while(num > 0);
            fin.close();
        }
        else
        {
            // save the file locally
            LocalFile = new File(remoteFileName);
            FileOutputStream fout = new FileOutputStream(LocalFile);
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
                    fout.write(buf, 0, num);
                    lengthOfFile = lengthOfFile - num;
                }
            }while((num > 0) && (lengthOfFile > 0));
            fout.close();
        }

        // read end of reply
        int b = inFromServer.read();
        if('2' != b)
        {
            System.err.println("ERROR: reply invalid (4) : " + (char) b + "!");
            b = inFromServer.read();
            System.err.println(" " + (char) b );
            b = inFromServer.read();
            System.err.println(" " + (char) b );
            b = inFromServer.read();
            System.err.println(" " + (char) b );
            return -8;
        }
        b = inFromServer.read();
        if(':' != b)
        {
            System.err.println("ERROR: reply invalid (5)!");
            return -9;
        }
        b = inFromServer.read();
        if('\n' != b)
        {
            System.err.println("ERROR: reply invalid (6)!");
            return -10;
        }
        if("compare".equals(command))
        {
            if(true == identical)
            {
                return 0;
            }
            else
            {
                return 1;
            }
        }
        return 0;
    }

    public int executeCommand()
    {
        try
        {
            int res = sendRequest();
            if(0 != res)
            {
                // sending the request did not work
                return res;
            }
            // Request has been send to the server

            // Parse reply
            int b = inFromServer.read();
            if('2' != b)
            {
                System.err.println("ERROR: reply invalid (1)!");
                return -4;
            }
            b = inFromServer.read();
            if(':' != b)
            {
                System.err.println("ERROR: reply invalid (2)!");
                return -5;
            }
            b = inFromServer.read();
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
                    }while('\n' != b);
                }
                catch (IOException e)
                {
                    isConnected = false;
                    e.printStackTrace();
                    System.err.println("ERROR: IOException !");
                }
                System.out.println("Reason: " + Reason.toString());
                return err;
            }
            b = inFromServer.read();
            if(':' != b)
            {
                System.err.println("ERROR: reply invalid (3)!");
                return -7;
            }
            StringBuffer DataSections = new StringBuffer();
            do {
                b = inFromServer.read();
                DataSections.append((char)b);
            }while('\n' != b);
            String[] sections = DataSections.toString().split(":");
            if(true == receiveFile)
            {
                res = handleReceiveFile(sections);
            }
            return res;
        }
        catch (IOException e)
        {
            isConnected = false;
            e.printStackTrace();
            System.err.println("ERROR: IOException !");
        }
        System.err.println("ERROR: reply invalid (7)!");
        return -11;
    }

    public void connectTo(String serverURL, int serverPort)
    {
        try
        {
            clientSocket = new Socket(serverURL, serverPort);
            outToServer = new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = clientSocket.getInputStream();
            isConnected = true;
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void disconnect()
    {
        try
        {
            clientSocket.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        isConnected = false;
    }
}
