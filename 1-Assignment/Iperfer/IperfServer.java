import java.io.*;
import java.net.*;

public class IperfServer
	{

	
	

	public static void main(String[] args)
		{

		// if(args.length != 1)
		// {
		// System.err.println("Usage: java EchoServer <port number>");
		// System.exit(1);
		// }

		int portNumber = Integer.parseInt("7000");
		int time = Integer.parseInt("10");
		byte[] oneKilo = new byte[1024];
		
		InputStream inFromClient;
		
		
		
		try
			{
			ServerSocket serverSocket = new ServerSocket(portNumber);
			System.out.println("Server Started..");
			Socket clientSocket = serverSocket.accept();
			System.out.println("Server Accepted..");

			
			inFromClient = clientSocket.getInputStream();
			
			// start timing
			int numbBytesRead = 0;
			long numKilosRec = 0;

			long startTime = System.currentTimeMillis();
			while((numbBytesRead = inFromClient.read(oneKilo, 0, 1024)) != -1 )
				{
				numKilosRec++;
				}
			
			long endTime = System.currentTimeMillis();
			
			long totalTime = (endTime - startTime)/1000;
			
			double throughput = (double) numKilosRec / (1024 * (double) totalTime);
			System.out.println("received=" + numKilosRec + " KB rate=" + throughput+" Mbps");
			
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
		}
	}
