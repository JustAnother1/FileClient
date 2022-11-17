package de.nomagic;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;



public class FileClient
{
    private int ServerPort = 4321;
    private String ServerURL = "127.0.0.1";
    private String ClientId = null;
    private String localFileName = null;
    private String remoteFileName = null;
    private String command = "";


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

    public int doTheWork()
    {
        if("".equals(command))
        {
            printHelpText();
            System.exit(-1);
        }
        // Connect to server
        FileInputStream fin = null;
        FileOutputStream fout = null;
        File LocalFile = null;
        try
        {
            FileRequest fr = new FileRequest(ServerURL, ServerPort, ClientId);
            if(null != localFileName)
            {
                LocalFile = new File(localFileName);
                if((true == "store".equals(command)) || (true == "update".equals(command) || (true == "compare".equals(command))))
                {
                    // read in local file
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
                    fin = new FileInputStream(LocalFile);
                    fr.addLocalFileForReading(fin, LocalFile.length());
                }
                else if(true == "get".equals(command))
                {
                    LocalFile = new File(remoteFileName);
                    fout = new FileOutputStream(LocalFile);
                    fr.addLocalFileForWriting(fout);
                }
                // else no files needed for "has",..
            }
            int res =  fr.makeRequest(command, remoteFileName);
            if(null != fin)
            {
                fin.close();
            }
            if(null != fout)
            {
                fout.close();
            }
            return res;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.err.println("ERROR: IOException !");
        }
        return -11;
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
                }
                else if(true == "-update".equals(args[i]))
                {
                    command = "update";
                    i++;
                    localFileName = args[i];
                    i++;
                    remoteFileName = args[i];
                }
                else if(true == "-get".equals(args[i]))
                {
                    command = "get";
                    i++;
                    remoteFileName = args[i];
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

}
