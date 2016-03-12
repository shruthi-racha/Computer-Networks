package edu.wisc.cs.sdn.vnet.rt;

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

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host
	 *            hostname for the router
	 */
	public Router(String host, DumpFile logfile)
		{
		super(host, logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
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
			}

		/********************************************************************/
		}

	private void handleArpPacket(Ethernet etherPacket, Iface inIface)
		{
		// TODO : adbhat : part3
		// if(ARP request)
		// { check target ip, send reply if necessary }
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

	private Iface createIcmpReplyPacket(Ethernet ether, IPv4 ipPacket, Iface inIface, byte type, byte code)
		{
		
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

				System.out.println("Best match's iface: " + outIface.getIpAddress() + " inIface: " + inIface.getIpAddress());

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
						System.out.println("ARPEntry lookup returned null");
					// TODO : adbhat : ICMP_DEST_HOST_UNREACHABLE
					
					
					return null;
					}
				// TODO set dest MAC
				ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

				// ---- END setting ethernet fields
				
				// START copying ICMP fields
				
//				byte dataArray[] = new byte[(ipPacket.getHeaderLength() * 4) + 4 + 8];
//				dataArray[0] = dataArray[1] = dataArray[2] = dataArray[3] = 0;
//				
//				ipPacket.setChecksum((short) 0);
//				byte[] tempArray = ipPacket.serialize();
//				int i;
//				for (i = 0;i < ipPacket.getHeaderLength() * 4;i++)
//					{
//					dataArray[i + 4] = tempArray[i];
//					}
//				for (int j = 0;j < 8;j++)
//					{
//					dataArray[i + 4 + j] = tempArray[i + j];
//					}
//
//				Data data = new Data();
//				data.setData(dataArray);

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

		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes()); // TODO : adbhat : outIface? remove?

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

		System.out.println("Best match's iface: " + outIface.getIpAddress() + " inIface: " + inIface.getIpAddress());

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
				System.out.println("ARPEntry lookup returned null");
			// TODO : adbhat : ICMP_DEST_HOST_UNREACHABLE

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
			return;
			}

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

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
				System.out.println("ARPEntry lookup returned null");
			// TODO : adbhat : ICMP_DEST_HOST_UNREACHABLE

			Ethernet icmpEther = new Ethernet();
			Iface icmpOutIface = this.createIcmpIpEthernetPacket(icmpEther, ipPacket, inIface, (byte) 3, (byte) 1);
			if(debug_level >= 1)
				System.out.println("*** -> Sending packet: " + icmpEther.toString().replace("\n", "\n\t"));
			this.sendPacket(icmpEther, icmpOutIface);

			// TODO : part3 - send ARP request, retry *3
			// {if no reply, DEST_HOST_UNREACHABLE}
			// if(reply) {do stuff.. add to cache, dequeue wait qs, send ..}
			return;
			}
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
		}
	}