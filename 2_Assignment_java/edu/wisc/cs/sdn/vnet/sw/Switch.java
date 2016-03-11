package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson
 */

class SwitchTableEntry
	{
	Iface outIface;
	Date inputTime;

	public SwitchTableEntry(Iface iface)
		{
		this.outIface = iface;
		this.inputTime = new Date();
		}

	}

public class Switch extends Device
	{
	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host
	 *            hostname for the router
	 */

	HashMap<MACAddress, SwitchTableEntry> macIfLookup;

	public Switch(String host, DumpFile logfile)
		{
		super(host, logfile);
		macIfLookup = new HashMap<MACAddress, SwitchTableEntry>();
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

		MACAddress src = etherPacket.getSourceMAC();
		MACAddress dest = etherPacket.getDestinationMAC();

		// add entry for src mac
		if(macIfLookup.containsKey(src))
			{
			macIfLookup.remove(src);
			}
		macIfLookup.put(src, new SwitchTableEntry(inIface));

		// lookup entry for dest mac - check invalid timestamp
		if(macIfLookup.containsKey(dest))
			{
			SwitchTableEntry outEntry = macIfLookup.get(dest);
			// handle time
			Date currentTime = new Date();
			if((currentTime.getTime() - outEntry.inputTime.getTime() > 15000))
				{
				// invalid entry
				macIfLookup.remove(dest);
				}
			}

		// entry + valid ts
		if(macIfLookup.containsKey(dest))
			{
			System.out.println("Found existing entry in switch table");
			SwitchTableEntry outEntry = macIfLookup.get(dest);
			this.sendPacket(etherPacket, outEntry.outIface);
			}
		// no entry
		else
			{
			System.out.println("No entry found.. going to broadcast");
			// broadcast?
			for (Iface iface : this.interfaces.values())
				{
				if(inIface != iface)
					{
					this.sendPacket(etherPacket, iface);
					}
				}
			}

		/********************************************************************/
		/* TODO: Handle packets */

		/********************************************************************/
		}
	}
