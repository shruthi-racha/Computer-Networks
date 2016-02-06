import java.io.*;
import java.net.*;
import java.util.*;

public class IperfClient
	{

	public static void main(String[] args)
		{
		// if(args.length != 2)
		// {
		// System.err.println("Usage: java EchoClient <host name> <port number>");
		// System.exit(1);
		// }

		// String hostName = args[0];

		// int portNumber = Integer.parseInt(args[1]);

		String hostName = "localhost";
		int portNumber = 7000;
		int time = 10;

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
