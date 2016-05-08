package edu.wisc.cs.sdn.simpledns;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.omg.CORBA.portable.InputStream;
import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataAddress;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

public class SimpleDNS2
	{
	public static String rootServerIP;

	public static void main(String[] args)
		{
		List<String> argsList = Arrays.asList(args);
		int indexIP, indexCSV;
		int portNo = 8053;
		String ec2FileName;

		indexIP = argsList.indexOf("-r") + 1;
		rootServerIP = argsList.get(indexIP);

		indexCSV = argsList.indexOf("-e") + 1;
		ec2FileName = argsList.get(indexCSV);

		try
			{
			System.out.println("Socket Creation");

			ServerSocket serverSocket = new ServerSocket(portNo);
			Socket clientSocket = serverSocket.accept();
			System.out.println("Waiting for Input");
			InputStream stream = (InputStream) clientSocket.getInputStream();
			ByteArrayOutputStream opStream = new ByteArrayOutputStream();
			byte[] data = new byte[1000000];
			int bytesRead = -1;
			while (stream.read(data) != -1)
				{
				opStream.write(data, 0, bytesRead);
				System.out.println("Read in the byte array output stream");
				}
			int count = stream.read(data);
			DNS dnsQuery = DNS.deserialize(data, count);

			System.out.println("Received a DNS Query");

			DNS dnsReply = handleDNSQuery(dnsQuery);
			OutputStream outStream = (OutputStream) clientSocket.getOutputStream();
			byte[] outData = dnsReply.serialize();
			outStream.write(outData);

			serverSocket.close();

			}
		catch(IOException e)
			{
			System.out.println("Exception when listening on port or for conection" + portNo);
			System.out.println(e.getMessage());
			}
		}

	public static DNS handleDNSQuery(DNS dnsQuery)
		{
		if(dnsQuery.getOpcode() == 0)
			{
			boolean recursionDesired = dnsQuery.isRecursionDesired();
			
			System.out.println("DNS Query Handling");
			if(!recursionDesired)
				{
				return queryDNSServer(rootServerIP, dnsQuery);
				}

			DNSQuestion question = dnsQuery.getQuestions().get(0);
			ArrayList<DNSResourceRecord> finalAnswers = new ArrayList<DNSResourceRecord>();

			String currDNSServerIP = rootServerIP;
			boolean queryDone = false;
			boolean reset = false;

			while (true)
				{
				DNS dnsReply = queryDNSServer(currDNSServerIP, dnsQuery);
				for (DNSResourceRecord answer : dnsReply.getAnswers())
					{
					finalAnswers.add(answer);
					if(answer.getType() == question.getType())
						{
						queryDone = true;
						}
					else if(answer.getType() == DNS.TYPE_CNAME)
						{
						question.setName(answer.getName());
						ArrayList<DNSQuestion> newQuestionList = new ArrayList<DNSQuestion>();
						newQuestionList.add(question);
						dnsQuery.setQuestions(newQuestionList);
						currDNSServerIP = rootServerIP;
						reset = true;
						}
					}
				if(reset)
					{
					reset = false;
					continue;
					}
				if(queryDone)
					{
					break;
					}
				DNSResourceRecord additional = (DNSResourceRecord) dnsReply.getAdditional().get(0);
				currDNSServerIP = ((DNSRdataAddress) additional.getData()).getAddress().toString();
				}

			dnsQuery.setAnswers(finalAnswers);
			return dnsQuery;

			}
		else
			{
			// Do nothing
			}
		return null;
		}

	public static DNS queryDNSServer(String ip, DNS dnsQuery)
		{
		String hostName = ip;
		int dnsServerPort = 53;
		// add rootserver ips 198.41.0.4

		try(Socket socket = new Socket(hostName, dnsServerPort);)
			{
			OutputStream outStream = (OutputStream) socket.getOutputStream();
			byte[] outData = dnsQuery.serialize();
			outStream.write(outData);

			InputStream stream = (InputStream) socket.getInputStream();
			byte[] data = new byte[1000000];
			int count = stream.read(data);
			DNS dnsReply = DNS.deserialize(data, count);

			return dnsReply;
			}
		catch(IOException e)
			{
			System.err.println("Could not perform I/O for the connection to" + hostName);
			System.exit(1);
			}
		return null;
		}
	}
