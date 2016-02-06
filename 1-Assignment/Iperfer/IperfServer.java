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

			long totalTime = (endTime - startTime) / 1000;

			double throughput = (double) numKilosRec / (1000 * (double) totalTime);
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
		}
	}
