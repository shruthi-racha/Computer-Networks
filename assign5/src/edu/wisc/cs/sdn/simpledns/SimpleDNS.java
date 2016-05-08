package edu.wisc.cs.sdn.simpledns;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import edu.wisc.cs.sdn.simpledns.packet.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class SimpleDNS
	{

	public static String rootServerIP;
	public static HashMap<String, String> ec2_ipMask_locations;
	public static HashMap<Integer, HashMap<Integer, String>> ec2_mask_ip;

	public static void main(String[] args)
		{
		System.out.println("Hello, DNS!");

		List<String> argsList = Arrays.asList(args);
		int portNo = 8053;
		int indexIP, indexCSV;
		rootServerIP = null;
		String ec2FileName = null;

		indexIP = argsList.indexOf("-r") + 1;
		rootServerIP = argsList.get(indexIP);

		indexCSV = argsList.indexOf("-e") + 1;
		ec2FileName = argsList.get(indexCSV);

		loadEc2DataFromFile(ec2FileName);

		DatagramSocket serverSock = null;
		byte udp_bytes[] = new byte[4096];

		try
			{
			serverSock = new DatagramSocket(portNo);
			}
		catch(SocketException e)
			{
			System.out.println("Server initialization / binding failed");
			e.printStackTrace();
			serverSock.close();
			System.exit(-1);
			}

		System.out.println("Socket opened, listening..");
		try
			{
			while (true)
				{
				DatagramPacket dns_udp = new DatagramPacket(udp_bytes, udp_bytes.length);

				serverSock.receive(dns_udp);

				DNS dnsQuery = DNS.deserialize(udp_bytes, dns_udp.getLength());

				System.out.println("\nDNS QUERY :");
				System.out.println(dnsQuery.toString());
				System.out.println("END OF DNS QUERY.\n");

				DNS dnsReply = handleDNSQuery(dnsQuery);

				if(dnsReply != null)
					{
					sendUdpReply(dnsReply, dns_udp, serverSock);
					}
				else
					{
					System.out.println("PROBLEM : DNS reply is null");
					}
				}
			}
		catch(IOException e)
			{
			System.out.println("Packet receipt failed");
			e.printStackTrace();
			}
		finally
			{
			serverSock.close();
			}

		}

	private static DNS handleDNSQuery(DNS dnsQuery)
		{

		// drop silently
		if(dnsQuery.getOpcode() != DNS.OPCODE_STANDARD_QUERY)
			{
			return null;
			}

		boolean recursionDesired = dnsQuery.isRecursionDesired();

		System.out.println("DNS Query Handling, Recursion : " + recursionDesired);
		if(!recursionDesired)
			{
			System.out.println("No Recursion, Querying : " + rootServerIP);
			return queryDNSServer(rootServerIP, dnsQuery);
			}

		// Recursion Desired

		// List<DNSQuestion> questions = dnsQuery.getQuestions(); only one question
		if(dnsQuery.getQuestions().size() < 1)
			{
			System.out.println("handleDNSQuery : Num of questions is insufficient");
			return null;
			}

		DNSQuestion question = dnsQuery.getQuestions().get(0);
		String originalQName = question.getName();

		if(question.getType() != DNS.TYPE_A && question.getType() != DNS.TYPE_AAAA && question.getType() != DNS.TYPE_CNAME && question.getType() != DNS.TYPE_NS)
			{
			System.out.println("handleDNSQuery : not supported type");
			return null;
			}

		List<DNSResourceRecord> finalAnswers = new ArrayList<DNSResourceRecord>();
		List<DNSResourceRecord> finalAuthorities = new ArrayList<DNSResourceRecord>();
		List<DNSResourceRecord> finalAdditional = new ArrayList<DNSResourceRecord>();

		String currDNSServerIP = rootServerIP;
		boolean queryDone = false;
		boolean reset = false;
		DNS dnsReply;
		while (true)
			{
			System.out.println("Recursively querying " + currDNSServerIP);
			dnsReply = queryDNSServer(currDNSServerIP, dnsQuery);

			System.out.println("\nDNS REPLY : " + dnsReply.toString());

			// Check answers if present
			for (DNSResourceRecord answer : dnsReply.getAnswers())
				{
				System.out.println("ANSWER RECORD: ");
				System.out.println(answer.toString());
				System.out.println("END ANSWER RECORD: ");
				// Type Matched
				if(answer.getType() == question.getType())
					{
					queryDone = true;

					// set answers etc
					finalAnswers.addAll(dnsReply.getAnswers());
					finalAuthorities.addAll(dnsReply.getAuthorities());
					finalAdditional.addAll(dnsReply.getAdditional());

					// A : Check and add TXT for EC2
					if(answer.getType() == DNS.TYPE_A)
						{
						DNSRdataAddress ansData = (DNSRdataAddress) answer.getData();
						String ansIpString = ansData.toString();
						String ec2_match_location = lookupInEc2File(ansIpString);

						// if match
						if(ec2_match_location != null)
							{
							DNSRdataString txtString = new DNSRdataString();
							txtString.setString(ec2_match_location + "-" + ansIpString);

							DNSResourceRecord txtAnswer = new DNSResourceRecord(question.getName(), DNS.TYPE_TXT, txtString);
							finalAnswers.add(txtAnswer);
							}
						}
					}
				else if((question.getType() == DNS.TYPE_A || question.getType() == DNS.TYPE_AAAA) && answer.getType() == DNS.TYPE_CNAME)
					{

					finalAnswers.addAll(dnsReply.getAnswers());

					System.out.println("OLD question :" + question.toString());
					DNSRdataName ansData = (DNSRdataName) answer.getData();
					question.setName(ansData.getName());
					System.out.println("NEW question :" + question.toString());

					List<DNSQuestion> newQuestionList = new ArrayList<DNSQuestion>();
					newQuestionList.add(question);
					dnsQuery.setQuestions(newQuestionList);
					currDNSServerIP = rootServerIP;
					reset = true; // TODO : shruthir : should we break ?
					System.out.println("RESETTING..");
					}
				}
			if(reset)
				{
				reset = false;
				continue;
				}
			if(queryDone)
				{
				System.out.println("handleDNSQuery : Query DONE!!");
				break;
				}

			// Fall back to Additional section
			boolean foundAdd = false;
			for (int i = 0;i < dnsReply.getAdditional().size();i++)
				{
				DNSResourceRecord additional = (DNSResourceRecord) dnsReply.getAdditional().get(i);

				if(additional.getType() == DNS.TYPE_A)
					{
					finalAnswers.add(additional);
					currDNSServerIP = ((DNSRdataAddress) additional.getData()).getAddress().toString();
					foundAdd = true;
					break;
					}
				}

			if(foundAdd == false)
				{
				boolean foundAuth = false;
				// Fallback to authority
				for (int i = 0;i < dnsReply.getAuthorities().size();i++)
					{
					DNSResourceRecord authority = (DNSResourceRecord) dnsReply.getAuthorities().get(i);

					if(authority.getType() == DNS.TYPE_NS)
						{
						System.out.println("OLD question :" + question.toString());
						DNSRdataName ansData = (DNSRdataName) authority.getData();
						question.setName(ansData.getName());
						System.out.println("NEW question :" + question.toString());

						List<DNSQuestion> newQuestionList = new ArrayList<DNSQuestion>();
						newQuestionList.add(question);
						dnsQuery.setQuestions(newQuestionList);
						currDNSServerIP = rootServerIP;
						reset = true; // TODO : shruthir : should we break ?
						System.out.println("RESETTING..");
						foundAuth = true;
						break;
						}
					}
				if(foundAuth)
					{
					reset = false;
					continue;
					}
				}

			if(currDNSServerIP.startsWith("/"))
				{
				currDNSServerIP = currDNSServerIP.substring(1);
				}

			}
		// loop breaks only if queryDone

		// TODO : dedup finalAnswers

		finalAnswers = dedupeList(finalAnswers);
		finalAdditional = dedupeList(finalAdditional);
		finalAuthorities = dedupeList(finalAuthorities);

		dnsReply.setAnswers(finalAnswers);
		dnsReply.setAdditional(finalAdditional);
		dnsReply.setAuthorities(finalAuthorities);

		// reset question section
		dnsReply.getQuestions().get(0).setName(originalQName);
		// System.out.println("Setting back to Original " + originalQuestion);

		return dnsReply;
		}

	private static List<DNSResourceRecord> dedupeList(List<DNSResourceRecord> list)
		{
		Set<DNSResourceRecord> s = new LinkedHashSet<>(list);
		ArrayList<DNSResourceRecord> finalList = new ArrayList<DNSResourceRecord>(s);
		return finalList;
		}

	private static DNS queryDNSServer(String dnsServerIp, DNS dnsQuery)
		{

		int dnsPort = 53;
		byte[] buf;
		buf = dnsQuery.serialize();
		InetAddress serverIpAddr = null;
		DatagramSocket socket = null;
		DNS dnsReply = null;

		try
			{
			// Create local socket
			socket = new DatagramSocket();

			// Send DNS query
			serverIpAddr = InetAddress.getByName(dnsServerIp);
			DatagramPacket packet = new DatagramPacket(buf, buf.length, serverIpAddr, dnsPort);
			socket.send(packet);

			// Receive response
			buf = new byte[10000];
			packet = new DatagramPacket(buf, buf.length);
			socket.receive(packet);

			dnsReply = DNS.deserialize(packet.getData(), packet.getLength());

			}
		catch(SocketException e)
			{
			System.out.println("Socket problem in queryDNSServer");
			e.printStackTrace();
			System.exit(1);
			}
		catch(UnknownHostException e)
			{
			System.out.println("InetAddrss parse failed : " + dnsServerIp);
			e.printStackTrace();
			}
		catch(IOException e)
			{
			System.out.println("queryDNSServer : packet send or receive failed");
			e.printStackTrace();
			}
		finally
			{
			socket.close();
			}

		return dnsReply;
		}

	private static void sendUdpReply(DNS dnsReply, DatagramPacket originalRequest, DatagramSocket serverSock)
		{

		System.out.println("\nGOING TO SEND FINAL DNS REPLY");
		System.out.println(dnsReply.toString());
		System.out.println();

		byte[] buf;
		buf = dnsReply.serialize();

		InetAddress requestorIp = originalRequest.getAddress();
		int dnsPort = originalRequest.getPort();

		try
			{
			// Send DNS response
			DatagramPacket packet = new DatagramPacket(buf, buf.length, requestorIp, dnsPort);
			serverSock.send(packet);
			}
		catch(SocketException e)
			{
			System.out.println("Socket problem in queryDNSServer");
			e.printStackTrace();
			}
		catch(IOException e)
			{
			System.out.println("queryDNSServer : packet send or receive failed");
			e.printStackTrace();
			}

		}

	private static String lookupInEc2File(String ipString)
		{
		if(ipString.startsWith("/"))
			{
			ipString = ipString.substring(1);
			}

		int targetIp = convertIpAddressToInt(ipString);

		System.out.println("lookupInEc2File : Looking up " + ipString + " in the EC2 file = " + targetIp);

		// prefix match! TODO
		ArrayList<Integer> masks = new ArrayList(ec2_mask_ip.keySet());
		Collections.sort(masks);
		for (int maskVal : masks)
			{
			if(ec2_mask_ip.get(maskVal) != null)
				{
				HashMap<Integer, String> ip_locations = ec2_mask_ip.get(maskVal);
				for (Integer ip : ip_locations.keySet())
					{
					if((ip & maskVal) == (targetIp & maskVal))
						{
						return ip_locations.get(ip);
						}
					}
				}
			}
		return null;
		}

	private static void loadEc2DataFromFile(String ec2FileName)
		{
		ec2_ipMask_locations = new HashMap<String, String>();
		ec2_mask_ip = new HashMap<Integer, HashMap<Integer, String>>();
		File f = new File(ec2FileName);
		List<String> ec2_entries = null;
		try
			{
			ec2_entries = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);

			for (String ec2_entry : ec2_entries)
				{
				// System.out.println("EC2 entry" + ec2_entry);
				if(!ec2_entry.contains(","))
					{
					continue;
					}
				String ip_port = ec2_entry.split(",")[0];
				String location = ec2_entry.split(",")[1];
				String mask = null;
				System.out.println(ip_port + "\t" + location);
				if(ip_port.contains("/"))
					{
					mask = ip_port.split("/")[1];
					ip_port = ip_port.split("/")[0];

					ec2_ipMask_locations.put(ip_port, location);
					System.out.println("Mask : " + mask);
					int maskVal = Integer.parseInt(mask);
					if(!ec2_mask_ip.containsKey(maskVal))
						{
						ec2_mask_ip.put(maskVal, new HashMap<Integer, String>());
						}
					HashMap<Integer, String> ip_location = ec2_mask_ip.get(maskVal);
					ip_location.put(convertIpAddressToInt(ip_port.trim()), location);

					}

				}
			System.out.println("EC2 file - Num Entries : " + ec2_ipMask_locations.size());
			}
		catch(IOException e1)
			{
			System.out.println("File could not be read " + f.toPath());
			e1.printStackTrace();
			}
		}

	private static int convertIpAddressToInt(String ipAddrStr)
		{
		InetAddress inetAddr;
		try
			{
			inetAddr = InetAddress.getByName(ipAddrStr);
			byte[] b = inetAddr.getAddress();
			int i = ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | ((b[3] & 0xFF) << 0);
			return i;
			}
		catch(UnknownHostException e)
			{
			System.out.println("convertIpAddressToInt: Error in parsing ipaddr: " + ipAddrStr);
			e.printStackTrace();
			}
		return 0;
		}

	}
