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

	private long UNSOL_PERIOD = 10000;

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
			System.out.println("Router table not provided");
			}
		else
			{
			System.out.println("Route table provided");
			}
		}

	public void runRip()
		{
		// TODO shruthir : default route initialization
		initializeRouteTable();
		// Run RIP

		// Running RIP :
		// when init - send RIP request on all ifaces

		// TODO - done - shruthir : send RIP request
		sendRipRequest();

		// Thread 1
		// every 10 seconds - unsolicited RIP response on all interfaces to
		// broadcast address

		Thread thread = new Thread(new Runnable()
			{
				public void run()
					{
					while (true)
						{
						sendRipResponse(false, null, null, null, null, null);
						try
							{
							Thread.sleep(UNSOL_PERIOD);
							}
						catch(InterruptedException e)
							{
							e.printStackTrace();
							}
						}
					}
			});
		thread.start();
		}

	private void sendRipRequest()
		{
		System.out.println("Going to send RIP request on all interfaces..");
		for (Iface iface : this.interfaces.values())
			{
			System.out.println("Going to send RIP request on interface.." + iface.getIpAddress());
			// Send RIP Request
			RIPv2 ripReq = new RIPv2();
			UDP udpReq = new UDP();
			IPv4 ipReq = new IPv4();
			Ethernet etherReq = new Ethernet();

			// Set ripReq
			ripReq.setCommand(RIPv2.COMMAND_REQUEST);
			ArrayList<RIPv2Entry> ripReqList = new ArrayList<RIPv2Entry>();
			RIPv2Entry ripEntry = new RIPv2Entry();
			ripEntry.setAddressFamily((short) 0);
			ripEntry.setMetric(16);
			ripReqList.add(ripEntry);
			ripReq.setEntries(ripReqList);
			// ripReq.serialize(); ?

			udpReq.setSourcePort(UDP.RIP_PORT);
			udpReq.setDestinationPort(UDP.RIP_PORT);
			udpReq.setChecksum((short) 0);
			udpReq.setPayload(ripReq);
			udpReq.serialize();

			ipReq.setPayload(udpReq);
			ipReq.setSourceAddress(iface.getIpAddress());
			ipReq.setDestinationAddress("224.0.0.9");
			ipReq.setProtocol(IPv4.PROTOCOL_UDP);
			ipReq.setChecksum((short) 0);
			ipReq.setTtl((byte) 64);
			ipReq.serialize();
			// ipReq set some more

			etherReq.setSourceMACAddress(iface.getMacAddress().toBytes());
			etherReq.setDestinationMACAddress("ff:ff:ff:ff:ff:ff");
			etherReq.setEtherType(Ethernet.TYPE_IPv4);
			etherReq.setPayload(ipReq);

			if(debug_level >= 1)
				System.out.println("*** -> Sending RIP Req packet: " + etherReq.toString().replace("\n", "\n\t"));
			this.sendPacket(etherReq, iface);
			}

		}

	// TODO : shruthir : Part4

	public void sendRipResponse(boolean solicited, Ethernet etherPacket, IPv4 ipPacket, UDP udpPacket, RIPv2 ripPacket, Iface inIface)
		{

		// Solicited Response - reply to our request
		// destination IP in request is NOT the multicast IP
		if(solicited == true)
			{
			RIPv2 ripResp = new RIPv2();
			UDP udpResp = new UDP();
			IPv4 ipResp = new IPv4();
			Ethernet etherResp = new Ethernet();

			// Set ripResp
			ripResp.setCommand(RIPv2.COMMAND_RESPONSE);
			List<RIPv2Entry> ripRespList = this.routeTable.getRIPEntries();

			// TODO : shruthir : part4 : fill in from route table

			ripResp.setEntries(ripRespList);
			// ripResp.serialize(); ?

			// also figure out iface check shruthi

			// END TODO : shruthir : part4 : fill in from route table

			udpResp.setSourcePort(UDP.RIP_PORT);
			udpResp.setDestinationPort(UDP.RIP_PORT);
			udpResp.setChecksum((short) 0);
			udpResp.setPayload(ripResp);
			udpResp.serialize();

			ipResp.setPayload(udpResp);
			ipResp.setSourceAddress(inIface.getIpAddress());
			ipResp.setDestinationAddress(ipPacket.getSourceAddress());
			ipResp.setProtocol(IPv4.PROTOCOL_UDP);
			ipResp.setChecksum((short) 0);
			ipResp.setTtl((byte) 15);
			ipResp.serialize();
			// ipResp set some more

			etherResp.setSourceMACAddress(inIface.getMacAddress().toBytes());
			etherResp.setDestinationMACAddress(etherPacket.getSourceMACAddress());
			etherResp.setEtherType(Ethernet.TYPE_IPv4);
			etherResp.setPayload(ipResp);

			if(debug_level >= 1)
				System.out.println("*** -> Sending RIP Resp packet: " + etherResp.toString().replace("\n", "\n\t"));
			this.sendPacket(etherResp, inIface);

			}

		// Unsolicited Response - 10s update
		// destination IP in request is the multicast IP (224.0.0.9)
		else
			{
			for (Iface iface : this.interfaces.values())
				{
				RIPv2 ripResp = new RIPv2();

				UDP udpResp = new UDP();
				IPv4 ipResp = new IPv4();
				Ethernet etherResp = new Ethernet();

				// Set ripResp
				ripResp.setCommand(RIPv2.COMMAND_RESPONSE);
				List<RIPv2Entry> ripRespList = this.routeTable.getRIPEntries();

				// TODO : shruthir : part4 : fill in from route table

				ripResp.setEntries(ripRespList);
				// ripResp.serialize(); ?

				// END TODO : shruthir : part4 : fill in from route table

				udpResp.setSourcePort(UDP.RIP_PORT);
				udpResp.setDestinationPort(UDP.RIP_PORT);
				udpResp.setChecksum((short) 0);
				udpResp.setPayload(ripResp);
				udpResp.serialize();

				ipResp.setPayload(udpResp);
				ipResp.setSourceAddress(iface.getIpAddress());
				ipResp.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
				ipResp.setProtocol(IPv4.PROTOCOL_UDP);
				ipResp.setChecksum((short) 0);
				// ipResp.setTtl((byte) 15);
				ipResp.serialize();
				// ipResp set some more

				etherResp.setSourceMACAddress(iface.getMacAddress().toBytes());
				etherResp.setDestinationMACAddress(MACAddress.valueOf("ff:ff:ff:ff:ff:ff").toBytes());
				etherResp.setEtherType(Ethernet.TYPE_IPv4);
				etherResp.setPayload(ipResp);

				if(debug_level >= 1)
					System.out.println("*** -> Sending RIP Resp broadcast packet: " + etherResp.toString().replace("\n", "\n\t"));
				this.sendPacket(etherResp, iface);

				}
			}
		}

	public void initializeRouteTable()
		{
		// TODO - done - shruthir : for each interface in interfaces add entry
		// to route table
		// RouteEntry = iface.Ip, iface.netmask, nextHop = 0
		// read para 2 of starting RIP for impl details
		this.routeTable.initializeRouteTable(this.interfaces.values());

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
		if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP)
			{
			UDP udpPacket = (UDP) ipPacket.getPayload();

			if(udpPacket.getSourcePort() == UDP.RIP_PORT && udpPacket.getDestinationPort() == UDP.RIP_PORT)
				{
				RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();

				if(ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE)
					{
					RouteEntry nextHopEntry = this.routeTable.lookup(ipPacket.getSourceAddress());
					if(nextHopEntry != null && (nextHopEntry.getGatewayAddress() == 0))
						{
						for (Iface iface : this.getInterfaces().values())
							{
							if(iface.getIpAddress() == ipPacket.getSourceAddress()) // routers own interface
								{
								return;
								}
							}
						List<RIPv2Entry> ripEntries = ripPacket.getEntries();

						for (RIPv2Entry entry : ripEntries)
							{
							String ipString = IPv4.fromIPv4Address((entry.getAddress() & entry.getSubnetMask()));
							if(ipString.startsWith("127") || ipString.startsWith("0.")) // check
								{
								continue;
								}
							if(entry.getMetric() < 1 || entry.getMetric() > 16)
								{
								continue;
								}

							RouteEntry routeTableEntry = this.routeTable.lookup(entry.getAddress());
							if(routeTableEntry == null) // entry not in table
								{
								// add entry
								/* routeTableEntry = new RouteEntry */
								this.routeTable.insert(entry.getAddress(), ipPacket.getSourceAddress(), entry.getSubnetMask(), inIface, (entry.getMetric() + 1));
								}
							else
								// entry in table check to update or no
								{
								if(entry.getMetric() + 1 < routeTableEntry.getMetric())
									{
									// update
									this.routeTable.update(entry.getAddress(), entry.getSubnetMask(), ipPacket.getSourceAddress(), inIface, (entry.getMetric() + 1));
									}
								else if(routeTableEntry.getGatewayAddress() == ipPacket.getSourceAddress())
									{
									// update
									this.routeTable.update(entry.getAddress(), entry.getSubnetMask(), ipPacket.getSourceAddress(), inIface, (entry.getMetric() + 1));
									if(entry.getMetric() + 1 >= 16)
										{
										this.routeTable.remove(entry.getAddress(), entry.getSubnetMask());
										}
									}
								else
									{
									continue;
									}
								}
							}
						}
					// Solicited Response - reply to our request
					// destination IP is NOT the multicast IP

					if(ipPacket.getDestinationAddress() != IPv4.toIPv4Address("224.0.0.9"))
						{
						// TODO
						// update route table. Fwd ?
						}
					// Unsolicited Response - 10s update
					// destination IP is the multicast IP (224.0.0.9)
					else if(ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9"))
						{
						// TODO
						// update route table. Fwd ?
						}
					}

				else if(ripPacket.getCommand() == RIPv2.COMMAND_REQUEST)
					{
					// Only one kind of request - Specific Request
					if(ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9"))
						{
						// update route table
						if(etherPacket.getDestinationMAC().isBroadcast())
							{
							// TODO : updates if any - DO NOT UPDATE RouteTable

							// send solicited response
							sendRipResponse(true, etherPacket, ipPacket, udpPacket, ripPacket, inIface);
							}
						}
					}
				return;
				}
			}

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
				arpMsgQueue.put(nextHop, new ArrayList<Ethernet>()); // create
																		// new
																		// queue
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

						if(arpMsgQueue.containsKey(nextHopFinal)) // check no
																	// reply
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
								arpMsgQueue.get(nextHopFinal).clear(); // drop
																		// packets
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
			System.out.println("Dropping"); // TODO : adbhat :
											// ICMP_DEST_HOST_UNREACHABLE

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
			// TODO : adbhat : ICMP_DEST_HOST_UNREACHABLE - dont do.. icmp
			// packet wont call itself

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
