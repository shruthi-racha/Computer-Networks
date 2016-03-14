package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;
import java.util.*;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
	{
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	int debug_level = 2;

	HashMap<Integer, ArrayList<Ethernet>> arpMsgQueue;

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host
	 *            hostname for the router
	 * @param rtProvided
	 */
	public Router(String host, DumpFile logfile, boolean rtProvided)
		{
		super(host, logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		arpMsgQueue = new HashMap<Integer, ArrayList<Ethernet>>();

		if(rtProvided == false)
			{
			// TODO shruthir : default route initialization
			initializeRouteTable();
			// Run RIP
			}

		}

	// Running RIP :
	// when init - send RIP request on all ifaces
	// Thread 1
	// every 10 seconds - unsolicited RIP response on all interfaces to broadcast address
	// Thread 2 - CONSIDER
	// in handlePacket, when we update the route table, consider undoing the change in 30seconds (thread.sleep)
	// PROBLEM : multiple sources for the same entry - need timestamp anyway
	// ALTERNATIVE
	// just keep lastUpdatedtimestamp, every second (via thread) / every time we do a lookup, invalidate (ref assign2)
	// NOTE: dont remove routeEntries provided by initialize.. either that, or call initilalize again everytime we lookup..
	
	// UPDATE : look at MACTable.java to see how MAC Table entries are invalidated every second.
	
	public void initializeRouteTable()
		{
		// TODO shruthir : for each interface in interfaces add entry to route table
		// RouteEntry = iface.Ip, iface.netmask, nextHop = 0
		// read para 2 of starting RIP for impl details

		}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
		{
		return this.routeTable;
		}

	/**
	 * Load a new routing table from a file.
	 * 
	 * @param routeTableFile
	 *            the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
		{
		if(!routeTable.load(routeTableFile, this))
			{
			System.err.println("Error setting up routing table from file " + routeTableFile);
			System.exit(1);
			}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
		}

	/**
	 * Load a new ARP cache from a file.
	 * 
	 * @param arpCacheFile
	 *            the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
		{
		if(!arpCache.load(arpCacheFile))
			{
			System.err.println("Error setting up ARP cache from file " + arpCacheFile);
			System.exit(1);
			}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
		}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * 
	 * @param etherPacket
	 *            the Ethernet packet that was received
	 * @param inIface
	 *            the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
		{
		System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/
		/* TODO: Handle packets */

		switch (etherPacket.getEtherType())
			{
			case Ethernet.TYPE_IPv4:
				this.handleIpPacket(etherPacket, inIface);
				break;
			// Ignore all other packet types, for now
			// TODO : adbhat : Part 3
			case Ethernet.TYPE_ARP:
				this.handleArpPacket(etherPacket, inIface);
				break;
			}

		/********************************************************************/
		}

	private void handleArpPacket(Ethernet etherPacket, Iface inIface)
		{
		// TODO : adbhat : part3
		// if(ARP request)
		// { check target ip, send reply if necessary }

		System.out.println("ARP packet received..");

		ARP arpPacket = (ARP) etherPacket.getPayload();

		ByteBuffer targetPA = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress());
		int targetIp = targetPA.getInt();

		if(arpPacket.getOpCode() == ARP.OP_REQUEST)
			{
			if(targetIp == inIface.getIpAddress())
				{
				System.out.println("Arp Request recvd for one of the router's interfaces..");

				// Create ARP reply
				Ethernet ether = new Ethernet();
				ARP arpReply = new ARP();

				ether.setEtherType(Ethernet.TYPE_ARP);
				ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
				ether.setDestinationMACAddress(etherPacket.getSourceMACAddress());

				arpReply.setHardwareType(ARP.HW_TYPE_ETHERNET);
				arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
				arpReply.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
				arpReply.setProtocolAddressLength((byte) 4);
				arpReply.setOpCode(ARP.OP_REPLY);
				arpReply.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
				arpReply.setSenderProtocolAddress(inIface.getIpAddress());
				arpReply.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
				arpReply.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());

				ether.setPayload(arpReply);
				this.sendPacket(ether, inIface);

				}
			else
				{
				System.out.println("Arp Request is not for one of the router's interfaces..");
				return;
				}
			}
		else if(arpPacket.getOpCode() == ARP.OP_REPLY)
			{
			// TODO : adbhat : part3
			// add an entry to the arp cache
			// dequeue any waiting packets for the searched Mac address, and
			// send

			System.out.println("Received ARP reply..");

			MACAddress senderMac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
			int senderIp = IPv4.toIPv4Address(arpPacket.getSenderProtocolAddress());

			if(debug_level >= 1)
				{
				System.out.println("Received ARP Reply Ip : " + IPv4.fromIPv4Address(senderIp) + " Mac: " + senderMac.toString());
				}
			this.arpCache.insert(senderMac, senderIp);

			if(!arpMsgQueue.containsKey(senderIp))
				{
				return;
				}
			if(arpMsgQueue.get(senderIp).isEmpty())
				{
				return;
				}

			List<Ethernet> packetsToSend = arpMsgQueue.get(senderIp);
			for (Ethernet packetToSend : packetsToSend)
				{
				packetToSend.setDestinationMACAddress(senderMac.toBytes());
				this.sendPacket(packetToSend, inIface);
				}
			arpMsgQueue.get(senderIp).clear();
			}
		}

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
		{
		// Make sure it's an IP packet
		if(etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
			{
			return;
			}

		// Get IP header
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if(origCksum != calcCksum)
			{
			if(debug_level >= 1)
				System.out.println("Invalid chksum.. dropping");
			return;
			}

		// TODO : shruthir : RIP Response or Request
		// if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP)
		// {
		// if(ipPacket.getDestinationAddress() == IPv4.parseOrWhatever("ff:ff:ff:ff:ff:ff"))
		// {
		// if RIP Request
		// update route table?
		// send Pv2 ripResp;
		// destination IP address and destination Ethernet address should be
		// the IP address and MAC address of the router interface that sent the request
		// else if RIP Response
		// updateRouteTable appropriately
		// }
		// }

		// Check TTL
		ipPacket.setTtl((byte) (ipPacket.getTtl() - 1));
		if(0 == ipPacket.getTtl())
			{
			if(debug_level >= 1)
				System.out.println("TTL is 0");
			// TODO : Done : adbhat : ICMP_TIME_EXCEEDED

			Ethernet ether = new Ethernet();
			Iface outIface = createIcmpIpEthernetPacket(ether, ipPacket, inIface, (byte) 11, (byte) 0);

			if(debug_level >= 1)
				System.out.println("*** -> Sending packet: " + ether.toString().replace("\n", "\n\t"));

			this.sendPacket(ether, outIface);
			return;
			}

		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();

		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
			{
			if(ipPacket.getDestinationAddress() == iface.getIpAddress())
				{
				if(debug_level >= 1)
					System.out.println("Dest addr equals an iface addr");
				// TODO : adbhat : DEST_PORT_UNREACHABLE and ECHO REPLY
				// check protocol field in the IP packet
				// if(IPv4.PROTOCOL_UDP or IPv4.PROTOCOL_TCP)
				// { do stuff}

				if(ipPacket.getProtocol() == IPv4.PROTOCOL_TCP || ipPacket.getProtocol() == IPv4.PROTOCOL_UDP)
					{
					Ethernet icmpEther = new Ethernet();
					Iface icmpOutIface = this.createIcmpIpEthernetPacket(icmpEther, ipPacket, inIface, (byte) 3, (byte) 3);
					if(debug_level >= 1)
						System.out.println("*** -> Sending packet: " + icmpEther.toString().replace("\n", "\n\t"));
					this.sendPacket(icmpEther, icmpOutIface);
					return;
					}
				// else if(IPv4.PROTOCOL_ICMP)
				// {
				// if(icmpheader.type!=8) // echo req
				// { return }
				// echo reply
				// }

				else if(ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP)
					{
					ICMP recvdIcmp = (ICMP) ipPacket.getPayload();
					if(recvdIcmp.getIcmpType() == 8)
						{
						Ethernet icmpEther = new Ethernet();
						Iface icmpOutIface = createIcmpReplyPacket(icmpEther, ipPacket, inIface, (byte) 0, (byte) 0);
						if(debug_level >= 1)
							System.out.println("*** -> Sending packet: " + icmpEther.toString().replace("\n", "\n\t"));
						this.sendPacket(icmpEther, icmpOutIface);
						return;
						}
					else
						{
						return;
						}
					}

				return;
				}
			}

		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
		}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
		{
		// Make sure it's an IP packet
		if(etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
			{
			return;
			}
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4) etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if(null == bestMatch)
			{

			if(debug_level >= 1)
				System.out.println("Route lookup returned null");
			// TODO : done : adbhat : ICMP_DEST_NET_UNREACHABLE

			Ethernet ether = new Ethernet();
			Iface outIface = this.createIcmpIpEthernetPacket(ether, ipPacket, inIface, (byte) 3, (byte) 0);
			if(debug_level >= 1)
				System.out.println("*** -> Sending packet: " + ether.toString().replace("\n", "\n\t"));
			this.sendPacket(ether, outIface);

			return;
			}

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if(outIface == inIface)
			{
			System.out.println("Dropping as outIface == inIface - fwdIpPacket");
			return;
			}

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if(0 == nextHop)
			{
			nextHop = dstAddr;
			}

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if(null == arpEntry)
			{
			if(debug_level >= 1)
				System.out.println("ARPEntry lookup returned null for: " + IPv4.fromIPv4Address(nextHop));

			// TODO : part3 - send ARP request, retry *3

			final int nextHopFinal = nextHop;
			final Iface inIfaceFinal = inIface;
			final IPv4 ipPacketFinal = (IPv4) ipPacket.clone();

			etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

			if(!arpMsgQueue.containsKey(nextHop)) // check if queue exists
				{
				arpMsgQueue.put(nextHop, new ArrayList<Ethernet>()); // create new queue
				}
			arpMsgQueue.get(nextHop).add(etherPacket); // add to queue

			Thread thread = new Thread(new Runnable()
				{

					public void run()
						{
						for (int i = 0;i < 3;i++)
							{
							// IF arpReply recvd..
							if(!arpMsgQueue.containsKey(nextHopFinal))
								break;
							if(arpMsgQueue.get(nextHopFinal).isEmpty())
								break;
							// ELSE arpReply not recvd..
							System.out.println("Going to send arp request " + i);

							System.out.println("Going to send arp request ");
							sendArpRequest(ipPacketFinal, inIfaceFinal, nextHopFinal);
							System.out.println("Sent request, going to sleep..");
							try
								{
								Thread.sleep(1000); // temp todo change later!
								}
							catch(InterruptedException e)
								{
								System.out.println("Interrupted exception in thread..");
								e.printStackTrace();
								}
							}

						// {if no reply, DEST_HOST_UNREACHABLE}
						// TODO : done : adbhat : ICMP_DEST_HOST_UNREACHABLE

						if(arpMsgQueue.containsKey(nextHopFinal)) // check no reply
							{
							if(!arpMsgQueue.get(nextHopFinal).isEmpty())
								{
								System.out.println("ARPRequest failed to get replies..");
								Ethernet icmpEther = new Ethernet();
								Iface icmpOutIface = createIcmpIpEthernetPacket(icmpEther, ipPacketFinal, inIfaceFinal, (byte) 3, (byte) 1);
								System.out.println("*** -> Sending ICMP: " + icmpEther.toString().replace("\n", "\n\t"));
								if(icmpOutIface != null)
									sendPacket(icmpEther, icmpOutIface);
								else
									{
									System.out.println("createIcmpIpEthernetPacket returned null instead of iface..");
									}
								arpMsgQueue.get(nextHopFinal).clear(); // drop packets
								}
							}
						}
				});
			// start timer-based thread
			thread.start();
			// if(reply) {do stuff in handleArpPacket}
			//
			return;
			}
		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());
		this.sendPacket(etherPacket, outIface);
		}

	private void sendArpRequest(IPv4 ipPacket, Iface inIface, int nextHop)
		{

		Ethernet ether = new Ethernet();

		ether.setEtherType(Ethernet.TYPE_ARP);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		ether.setDestinationMACAddress(MACAddress.valueOf("ff:ff:ff:ff:ff:ff").toBytes());

		ARP arpRequest = new ARP();
		arpRequest.setHardwareType(ARP.HW_TYPE_ETHERNET);
		arpRequest.setProtocolType(ARP.PROTO_TYPE_IP);
		arpRequest.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
		arpRequest.setProtocolAddressLength((byte) 4);
		arpRequest.setOpCode(ARP.OP_REQUEST);
		arpRequest.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
		arpRequest.setSenderProtocolAddress(inIface.getIpAddress());
		arpRequest.setTargetHardwareAddress(MACAddress.valueOf(0l).toBytes());
		arpRequest.setTargetProtocolAddress(nextHop);

		ether.setPayload(arpRequest);

		if(debug_level >= 1)
			System.out.println("*** -> Sending ARP Req packet: " + ether.toString().replace("\n", "\n\t"));
		// TODO shruthir : send out on all interfaces --- check
		for (Iface iface : this.interfaces.values())
			{
			this.sendPacket(ether, iface);
			}

		}

	private Iface createIcmpReplyPacket(Ethernet ether, IPv4 ipPacket, Iface inIface, byte type, byte code)
		{

		System.out.println("ENTERING function createIcmpReplyPacket");

		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());

		// ----
		// Find matching route table entry
		RouteEntry bestMatch = this.routeTable.lookup(ipPacket.getSourceAddress());

		// If no entry matched, do nothing
		if(null == bestMatch)
			{
			// TODO : adbhat : ICMP_DEST_NET_UNREACHABLE
			if(debug_level >= 1)
				System.out.println("Route lookup returned null");
			return null;
			}

		Iface outIface = bestMatch.getInterface();

		System.out.println("Best match's iface: " + IPv4.fromIPv4Address(outIface.getIpAddress()) + " inIface: " + IPv4.fromIPv4Address(inIface.getIpAddress()));

		// Set source MAC address in Ethernet header
		ether.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if(0 == nextHop)
			{
			if(debug_level >= 1)
				{
				System.out.println("Next hop is 0.. setting to original src addr");
				}
			nextHop = ipPacket.getSourceAddress();
			}

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if(null == arpEntry)
			{
			if(debug_level >= 1)
				System.out.println("ARPEntry lookup returned null for " + IPv4.fromIPv4Address(nextHop));
			System.out.println("Dropping"); // TODO : adbhat : ICMP_DEST_HOST_UNREACHABLE

			return null;
			}
		// TODO set dest MAC
		ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

		// ---- END setting ethernet fields

		// START copying ICMP fields

		// byte dataArray[] = new byte[(ipPacket.getHeaderLength() * 4) + 4 +
		// 8];
		// dataArray[0] = dataArray[1] = dataArray[2] = dataArray[3] = 0;
		//
		// ipPacket.setChecksum((short) 0);
		// byte[] tempArray = ipPacket.serialize();
		// int i;
		// for (i = 0;i < ipPacket.getHeaderLength() * 4;i++)
		// {
		// dataArray[i + 4] = tempArray[i];
		// }
		// for (int j = 0;j < 8;j++)
		// {
		// dataArray[i + 4 + j] = tempArray[i + j];
		// }
		//
		// Data data = new Data();
		// data.setData(dataArray);

		ICMP recvdIcmp = (ICMP) ipPacket.getPayload();
		ICMP icmp = new ICMP();
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);
		icmp.setPayload(recvdIcmp.getPayload());

		IPv4 ip = new IPv4();
		ip.setTtl((byte) 64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(ipPacket.getDestinationAddress());
		ip.setDestinationAddress(ipPacket.getSourceAddress());
		ip.setPayload(icmp);

		ip.setChecksum((short) 0);
		// ip.serialize();

		ether.setPayload(ip);
		return outIface;

		}

	private Iface createIcmpIpEthernetPacket(Ethernet ether, IPv4 ipPacket, Iface inIface, byte type, byte code)
		{

		System.out.println("ENTERING function createIcmpIpEthernetPacket");

		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		// TODO : adbhat : outIface? remove?

		// ----
		// Find matching route table entry
		RouteEntry bestMatch = this.routeTable.lookup(ipPacket.getSourceAddress());

		// If no entry matched, do nothing
		if(null == bestMatch)
			{
			// TODO : adbhat : ICMP_DEST_NET_UNREACHABLE
			if(debug_level >= 1)
				System.out.println("Route lookup returned null");
			return null;
			}

		Iface outIface = bestMatch.getInterface();

		System.out.println("Best match's iface: " + IPv4.fromIPv4Address(outIface.getIpAddress()) + " inIface: " + IPv4.fromIPv4Address(inIface.getIpAddress()));

		// Set source MAC address in Ethernet header
		ether.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if(0 == nextHop)
			{
			if(debug_level >= 1)
				{
				System.out.println("Next hop is 0.. setting to original src addr " + IPv4.fromIPv4Address(ipPacket.getSourceAddress()));
				}
			nextHop = ipPacket.getSourceAddress();
			}

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if(null == arpEntry)
			{
			if(debug_level >= 1)
				System.out.println("ARPEntry lookup returned null for " + IPv4.fromIPv4Address(nextHop));
			System.out.println("Dropping");
			// TODO : adbhat : ICMP_DEST_HOST_UNREACHABLE - dont do.. icmp packet wont call itself

			return null;
			}
		// TODO set dest MAC
		ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

		// ----

		byte dataArray[] = new byte[(ipPacket.getHeaderLength() * 4) + 4 + 8];
		dataArray[0] = dataArray[1] = dataArray[2] = dataArray[3] = 0;
		ipPacket.setChecksum((short) 0);
		byte[] tempArray = ipPacket.serialize();
		int i;
		for (i = 0;i < ipPacket.getHeaderLength() * 4;i++)
			{
			dataArray[i + 4] = tempArray[i];
			}
		for (int j = 0;j < 8;j++)
			{
			dataArray[i + 4 + j] = tempArray[i + j];
			}

		Data data = new Data();
		data.setData(dataArray);

		ICMP icmp = new ICMP();
		icmp.setIcmpType(type);
		icmp.setIcmpCode(code);
		icmp.setPayload(data);

		IPv4 ip = new IPv4();
		ip.setTtl((byte) 64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		ip.setSourceAddress(outIface.getIpAddress());
		ip.setDestinationAddress(ipPacket.getSourceAddress());
		ip.setPayload(icmp);

		ip.setChecksum((short) 0);
		// ip.serialize();

		ether.setPayload(ip);
		return outIface;

		}

	}
