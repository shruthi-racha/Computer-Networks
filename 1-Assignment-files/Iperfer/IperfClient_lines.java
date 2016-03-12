import java.io.*;
import java.net.*;
import java.util.*;
public class IperfClient_lines
	{

	public static void main(String[] args)
		{
//		if(args.length != 2)
//			{
//			System.err.println("Usage: java EchoClient <host name> <port number>");
//			System.exit(1);
//			}

		// String hostName = args[0];

		// int portNumber = Integer.parseInt(args[1]);
		
		String hostName = "localhost";
		int portNumber = 7000;
		BufferedReader stdIn;
		BufferedReader inFromServer;
		PrintWriter outToServer;
//		stdIn = new BufferedReader(new InputStreamReader(System.in));
		Scanner consoleIn = new Scanner(System.in);
		try
			{
			Socket echoSocket = new Socket(hostName, portNumber);
			System.out.println("Client.. created socket");
			
			outToServer = new PrintWriter(echoSocket.getOutputStream(), true);
			inFromServer = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
			
			String userInput;
			System.out.println("Client : going to read from console");
			while (consoleIn.hasNext())
				{
				userInput = consoleIn.next();
				if(userInput == null || userInput.equals("exit"))
					{
					
					break;
					}
				outToServer.println(userInput);
				System.out.println("echo: " + userInput);
				}
			
			System.out.println("Going to recv from server..");
			
			String recvedFromServer = inFromServer.readLine();
			System.out.println(recvedFromServer);
			
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
