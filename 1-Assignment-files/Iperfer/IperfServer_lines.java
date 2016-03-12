import java.io.*;
import java.net.*;

public class IperfServer_lines
	{

	public static void main(String[] args)
		{

		// if(args.length != 1)
		// {
		// System.err.println("Usage: java EchoServer <port number>");
		// System.exit(1);
		// }

		int portNumber = Integer.parseInt("7000");
		PrintWriter outToClient;
		BufferedReader inFromClient;

		try
			{
			ServerSocket serverSocket = new ServerSocket(portNumber);
			System.out.println("Server Started..");
			Socket clientSocket = serverSocket.accept();
			System.out.println("Server Accepted..");

			outToClient = new PrintWriter(clientSocket.getOutputStream(), true);
			inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

			// start timing
			String inputLine;
			System.out.println("Going to read....");
			while ((inputLine = inFromClient.readLine()) != null)
				{
				System.out.println(inputLine.toUpperCase());
				}
			// end timing
			outToClient.println("Thanks for theinfo.. bbye");
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
