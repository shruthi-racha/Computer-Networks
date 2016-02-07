import java.io.*;
import java.net.*;

public class Iperfer
	{

	public static void main(String[] args)
		{
		int numberOfArgs = args.length;
		String hostName = null, mode = null;
		int i = 0 , j = 0, time = 0, portNumber = 0;
		while( i < numberOfArgs)
			{
			//System.out.println("outer while");
				if(args[i].equals("-c"))
					{
						mode = "c";
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
									 try
										 {
									 portNumber = Integer.parseInt(args[j+1]);
										 }
									 catch(Exception e)
										 {
										 	System.out.println(e.getMessage());
										 }
									 if(portNumber < 1024 || portNumber > 65535)
										 {
										 	System.err.println("Error: port number must be in the range 1024 to 65535");
										 	System.exit(1);
										 }
									 }
								 else if (args[j].equals("-t"))
									 {
									 try
										 {
										 	time = Integer.parseInt(args[j+1]);
										 }
									 catch(Exception e)
									 {
									 	System.out.println(e.getMessage());
									 }
									 if(time <=0)
										 {
										 System.out.println("Time should be a positive value");
										 System.exit(1);
										 }
									 
									 }
								 j++;
								 }
							 break;
					}
				
			if(args[i].equals("-s"))
				{
				mode = "s";
				//Iperfer in Server mode
					if(args.length != 3) //Check for number of parameters
					 {
					 	System.err.println("Error: missing or additional arguments");
					 	System.exit(1);
					 }
					 while(j < numberOfArgs)
						 {
						 if (args[j].equals("-p"))
							 {
							 try
								 {
								 	portNumber = Integer.parseInt(args[j+1]);
								 }
							 catch(Exception e)
								 {
								 	System.out.println(e.getMessage());
								 }
							 if(portNumber < 1024 || portNumber > 65535)
								 {
								 	System.err.println("Error: port number must be in the range 1024 to 65535");
								 	System.exit(1);
								 }
							 }
						 j++;
						 }
					 break;
				}
			i++;
			}
		if(i == numberOfArgs)
			{
			System.err.println("Error: Invalid Mode");
		 	System.exit(1);
			}
switch(mode)
	{
	
	case "c":
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
				echoSocket.close();
				double throughput = (double) (numKilosSent*8) / (1000 * (double) time);
				System.out.println("sent=" + numKilosSent + " KB rate=" + throughput+" Mbps");
			
				
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
			break;
				
	case "s":
	
	//int portNumber = Integer.parseInt("7000");
			byte[] oneKilo = new byte[1000];
	
			InputStream inFromClient;
	
			try
				{
				ServerSocket serverSocket = new ServerSocket(portNumber);
				System.out.println("Server Started..");
				Socket clientSocket = serverSocket.accept();
				System.out.println("Server Accepted..");
	
				inFromClient = clientSocket.getInputStream();
	
				// start timing
				int numBytesRead = 0;
				long numKilosRec = 0;
				long totalNumBytesRead = 0;
	
				long startTime = System.currentTimeMillis();
				while ((numBytesRead = inFromClient.read(oneKilo, 0, 1000)) != -1)
					{
					numKilosRec++;
					totalNumBytesRead += numBytesRead;
					//System.out.println("numBytesRead : " + numbBytesRead);
					}
				
				long endTime = System.currentTimeMillis();
	
				inFromClient.close();
	
				long totalTime = (endTime - startTime) / 1000; // in seconds
	
				double throughput = (double) (totalNumBytesRead*8) / (1000000 * (double) totalTime);
				System.out.println("received=" + numKilosRec + " KB rate=" + throughput + " Mbps");
				//System.out.println("Actual Bytes Read : "+totalNumBytesRead);
	
				clientSocket.close();
				serverSocket.close();
				}
			catch(IOException e)
				{
				System.out.println("Exception caught when trying to listen on port " + portNumber + " or listening for a connection");
				System.out.println(e.getMessage());
				}
			finally
				{
	
				}
			break;
	
	default:
		{

		}
	}

		
		}//end of main
	}//end of class
