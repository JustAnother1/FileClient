package de.nomagic;

public class RequestVersion2
{
    private boolean isAddJob = false;
    private String JobSpec = "";
    private String name = "";
    private String type = "";


    public RequestVersion2(String request)
    {
        if(true == request.startsWith("2:add:"))
        {
            isAddJob = true;
            JobSpec = request.substring(6);
        }
        String[] parts = request.split(":");
        for(int i = 0; i < parts.length; i++)
        {
            String line = parts[i];
            line = line.trim();
            if(line.startsWith("name"))
            {
                name = line.substring(line.indexOf('=') + 1);
                name = name.trim();
            }
            if(line.startsWith("type"))
            {
                type = line.substring(line.indexOf('=') + 1);
                type = type.trim();
            }
        }
    }

    public boolean isAddJob()
    {
        return isAddJob;
    }

    public String getType()
    {
        return type;
    }

    public String getJobSpec()
    {
        // Request is add a Job!
        return JobSpec;
    }

    public String getClientId()
    {
        return name;
    }

}
