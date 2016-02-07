import java.io.*;
import java.net.*;
import java.util.*;

public class IperfClient
	{

	public static void main(String[] args)
		{
		//Input arguments parsing
		//Client
		int numberOfArgs = args.length;
		String hostName = null;
		int i = 0 , j = 0, time = 0, portNumber = 0;
		while( i < numberOfArgs)
			{
			//System.out.println("outer while");
				if(args[i].equals("-c"))
					{
						//System.out.println("Client mode");
						//Iperfer in Client mode
						 if(args.length != 7) //Check for number of parameters
							 {
							 	System.err.println("Error: missing or additional arguments");
							 	System.exit(1);
							 }
							 while(j < numberOfArgs)
								 {
								 if (args[j].equals("-h"))
									 {
									 hostName = args[j+1];
									 }
								 else if (args[j].equals("-p"))
									 {
									 portNumber = Integer.parseInt(args[j+1]);
									 if(portNumber < 1024 || portNumber > 65535)
										 {
										 	System.err.println("Error: port number must be in the range 1024 to 65535");
										 	System.exit(1);
										 }
									 }
								 else if (args[j].equals("-t"))
									 {
									 time = Integer.parseInt(args[j+1]);
									 }
								 j++;
								 }
					}
				
			if(args[i].equals("-s"))
				{
				//Iperfer in Server mode
				}
			i++;
			}

	    //String hostName = "localhost";
		//int portNumber = 7000;
		//int time = 10;

		byte[] onekiloMsg = new byte[1000];

		OutputStream outToServer;

		try
			{
			Socket echoSocket = new Socket(hostName, portNumber);
			System.out.println("Client.. created socket");

			outToServer = echoSocket.getOutputStream();

			long numKilosSent = 0;
			System.out.println("Client.. going to send for "+time+" seconds");
			long endTime = System.currentTimeMillis() + (time * 1000);
			do
				{
				outToServer.write(onekiloMsg);
				numKilosSent++;
				}
			while (System.currentTimeMillis() < endTime);
			outToServer.close();
			
			double throughput = (double) numKilosSent / (1000 * (double) time);
			System.out.println("sent=" + numKilosSent + " KB rate=" + throughput+" Mbps");

			echoSocket.close();
			}
		catch(UnknownHostException e)
			{
			System.err.println("Don't know about host " + hostName);
			System.exit(1);
			}
		catch(IOException e)
			{
			System.err.println("Couldn't get I/O for the connection to " + hostName);
			System.exit(1);
			}
		}
	}
