package edu.wisc.cs.sdn.apps.loadbalancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFOXMField;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionGotoTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sun.org.apache.bcel.internal.generic.DSTORE;
import edu.wisc.cs.sdn.apps.l3routing.L3Routing;
import edu.wisc.cs.sdn.apps.util.ArpServer;
import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.util.MACAddress;

public class LoadBalancer implements IFloodlightModule, IOFSwitchListener, IOFMessageListener
	{
	public static final String MODULE_NAME = LoadBalancer.class.getSimpleName();

	private static final byte TCP_FLAG_SYN = 0x02;

	private static final short IDLE_TIMEOUT = 20;

	// Interface to the logging system
	private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

	// Interface to Floodlight core for interacting with connected switches
	private IFloodlightProviderService floodlightProv;

	// Interface to device manager service
	private IDeviceService deviceProv;

	// Switch table in which rules should be installed
	private byte table;

	// Set of virtual IPs and the load balancer instances they correspond with
	private Map<Integer, LoadBalancerInstance> instances;

	/**
	 * Loads dependencies and initializes data structures.
	 */
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException
		{
		log.info(String.format("Initializing %s...", MODULE_NAME));

		// Obtain table number from config
		Map<String, String> config = context.getConfigParams(this);
		this.table = Byte.parseByte(config.get("table"));

		// Create instances from config
		this.instances = new HashMap<Integer, LoadBalancerInstance>();
		String[] instanceConfigs = config.get("instances").split(";");
		for (String instanceConfig : instanceConfigs)
			{
			String[] configItems = instanceConfig.split(" ");
			if(configItems.length != 3)
				{
				log.error("Ignoring bad instance config: " + instanceConfig);
				continue;
				}
			LoadBalancerInstance instance = new LoadBalancerInstance(configItems[0], configItems[1], configItems[2].split(","));
			this.instances.put(instance.getVirtualIP(), instance);
			log.info("Added load balancer instance: " + instance);
			}

		this.floodlightProv = context.getServiceImpl(IFloodlightProviderService.class);
		this.deviceProv = context.getServiceImpl(IDeviceService.class);

		/*********************************************************************/
		/* TODO: Initialize other class variables, if necessary */

		/*********************************************************************/
		}

	/**
	 * Subscribes to events and performs other startup tasks.
	 */
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException
		{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.floodlightProv.addOFMessageListener(OFType.PACKET_IN, this);

		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary */

		/*********************************************************************/
		}

	/**
	 * Event handler called when a switch joins the network.
	 * 
	 * @param DPID
	 *            for the switch
	 */
	@Override
	public void switchAdded(long switchId)
		{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));

		/*********************************************************************/
		/* TODO: Install rules to send: */
		/* (1) packets from new connections to each virtual load balancer IP to the controller */

		for (int virtIp : this.instances.keySet())
			{
			this.installFwdVirtualIpToControllerRule(sw, virtIp);

			/* (2) ARP packets to the controller (virtual MAX requests) */
			this.installFwdARPToControllerRule(sw, virtIp);
			}

		/* (3) all other packets to the next rule table in the switch (table 1, l3routing's default table) */
		// OFInstructionGotoTable L3Routing.table
		this.installDefaultRule(sw);

		/*********************************************************************/
		}

	private void installDefaultRule(IOFSwitch currSw)
		{
		// TODO Auto-generated method stub
		OFMatch matchCriteria = new OFMatch();

		// Specifically for changing dest

		OFInstructionGotoTable gotoTableInst = new OFInstructionGotoTable((byte) 1);

		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();

		instructionList.add(gotoTableInst);

		if(SwitchCommands.installRule(currSw, table, (short) (SwitchCommands.DEFAULT_PRIORITY), matchCriteria, instructionList) == false)
			{
			System.out.println("Default Rule not installed: sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		else
			{
			System.out.println("Default Rule installed : sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}

		}

	/**
	 * Handle incoming packets sent from switches.
	 * 
	 * @param sw
	 *            switch on which the packet was received
	 * @param msg
	 *            message from the switch
	 * @param cntx
	 *            the Floodlight context in which the message should be handled
	 * @return indication whether another module should also process the packet
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx)
		{
		// We're only interested in packet-in messages
		if(msg.getType() != OFType.PACKET_IN)
			{
			return Command.CONTINUE;
			}
		OFPacketIn pktIn = (OFPacketIn) msg;

		// Handle the packet
		Ethernet ethPkt = new Ethernet();
		ethPkt.deserialize(pktIn.getPacketData(), 0, pktIn.getPacketData().length);

		/*********************************************************************/
		// TODO:
		// 1. Send an ARP reply for ARP requests for virtual IPs; */
		if(ethPkt.getEtherType() == Ethernet.TYPE_ARP)
			{
			ARP arpReq = (ARP) ethPkt.getPayload();
			if(arpReq.getOpCode() == ARP.OP_REQUEST)
				{
				// if(arpReq.getTargetProtocolAddress())
				byte targetIpBytes[] = arpReq.getTargetProtocolAddress();
				int targetIp = IPv4.toIPv4Address(targetIpBytes);
				if(this.instances.get(targetIp) != null)
					{
					LoadBalancerInstance matchedInstance = this.instances.get(targetIp);
					Ethernet ether = new Ethernet();
					ARP arpReply = new ARP();

					ether.setEtherType(Ethernet.TYPE_ARP);
					ether.setSourceMACAddress(matchedInstance.getVirtualMAC());
					ether.setDestinationMACAddress(ethPkt.getSourceMACAddress());

					arpReply.setHardwareType(ARP.HW_TYPE_ETHERNET);
					arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
					arpReply.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
					arpReply.setProtocolAddressLength((byte) 4);
					arpReply.setOpCode(ARP.OP_REPLY);
					arpReply.setSenderHardwareAddress(matchedInstance.getVirtualMAC());
					arpReply.setSenderProtocolAddress(matchedInstance.getVirtualIP());
					arpReply.setTargetHardwareAddress(arpReq.getSenderHardwareAddress());
					arpReply.setTargetProtocolAddress(arpReq.getSenderProtocolAddress());
					ether.setPayload(arpReply);

					// use SwitchCommands.sendPacket
					System.out.println("*** -> LB Going to send ARP Reply packet: " + ether.toString().replace("\n", "\n\t"));
					SwitchCommands.sendPacket(sw, (short) pktIn.getInPort(), ether);
					}
				}
			}

		// 2. for TCP SYNs sent to a virtual IP, ie, new connection
		else if(ethPkt.getEtherType() == Ethernet.TYPE_IPv4)
			{
			IPv4 ipPacket = (IPv4) ethPkt.getPayload();
			int targetIp = ipPacket.getDestinationAddress();
			if(this.instances.get(targetIp) != null)
				{
				if(ipPacket.getProtocol() == IPv4.PROTOCOL_TCP)
					{
					TCP tcpPkt = (TCP) ipPacket.getPayload();
					if((tcpPkt.getFlags() & TCP_FLAG_SYN) > 0)
						{
						// is TCP SYN
						installConnectionReroutingRules(ipPacket, ethPkt);
						}
					}
				}
			}

		/*********************************************************************/

		// We don't care about other packets
		return Command.CONTINUE;
		}

	private void installConnectionReroutingRules(IPv4 ipPacket, Ethernet etherPacket)
		{
		// * a. select a host
		TCP tcpPkt = (TCP) ipPacket.getPayload();
		int targetIp = ipPacket.getDestinationAddress(); // equals matchedInst.getvirtIp
		int targetPort = tcpPkt.getDestinationPort();

		LoadBalancerInstance matchedInstance = this.instances.get(targetIp);
		byte[] targetMac = matchedInstance.getVirtualMAC();
		int mappedHostIp = matchedInstance.getNextHostIP();
		byte[] mappedHostMac = this.getHostMACAddress(mappedHostIp);

		// * b. install connection-specific rules to rewrite IP and MAC addresses;
		// TODO : adbhat : shruthir : Notify

		int clientIp = ipPacket.getSourceAddress();
		int clientPort = tcpPkt.getSourcePort();
		byte[] clientMac = etherPacket.getSourceMACAddress();

		String fwdKey = String.valueOf(clientIp) + "_" + String.valueOf(clientPort) + "_" + String.valueOf(targetIp) + "_" + String.valueOf(targetPort);
		String backKey = String.valueOf(mappedHostIp) + "_" + String.valueOf(targetPort) + "_" + String.valueOf(clientIp) + "_" + String.valueOf(clientPort);

		for (IOFSwitch currSw : this.floodlightProv.getAllSwitchMap().values())
			{
			installDestinationChangeRule(currSw, clientIp, clientPort, clientMac, targetIp, targetPort, targetMac, mappedHostIp, mappedHostMac);
			installSourceChangeRule(currSw, clientIp, clientPort, clientMac, targetIp, targetPort, targetMac, mappedHostIp, mappedHostMac);
			}

		}

	private void installDestinationChangeRule(IOFSwitch currSw, int clientIp, int clientPort, byte[] clientMac, int virtualIp, int virtualPort, byte[] virtualMac,
			int mappedHostIp, byte[] mappedHostMac)
		{
		// Dest is virtual ip
		System.out.println("\nLB : Install Dest Change Rule :  sw - " + String.valueOf(currSw.getId()));

		// Cnnxn rules - src ip, port, dest ip, port, Eth type, protocol
		// higher priority than the default (1). idle timeout of 20s

		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
		matchCriteria.setNetworkSource(clientIp);
		matchCriteria.setTransportSource((short) clientPort);
		matchCriteria.setNetworkDestination(virtualIp);
		matchCriteria.setTransportDestination((short) virtualPort);

		// Specifically for changing dest
		OFActionSetField act1 = new OFActionSetField();
		OFOXMField field1 = new OFOXMField(OFOXMFieldType.ETH_DST, mappedHostMac);
		act1.setField(field1);

		OFActionSetField act2 = new OFActionSetField();
		OFOXMField field2 = new OFOXMField(OFOXMFieldType.IPV4_DST, mappedHostIp);
		act2.setField(field2);

		List<OFAction> actionOps = new LinkedList<OFAction>();
		actionOps.add(act1);
		actionOps.add(act2);

		OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actionOps);
		OFInstructionGotoTable gotoTableInst = new OFInstructionGotoTable((byte) 1);

		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();

		instructionList.add(gotoTableInst);
		instructionList.add(applyActions);

		// install in this.table (0)
		SwitchCommands.removeRules(currSw, this.table, matchCriteria);

		if(SwitchCommands.installRule(currSw, table, (short) (SwitchCommands.DEFAULT_PRIORITY + 2), matchCriteria, instructionList, (short) 0, (short) 20) == false)
			{
			System.out.println("LB : Dest change Rule not installed: sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		else
			{
			System.out.println("LB : Dest change Rule installed : sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			System.out.println("Dest IP " + IPv4.fromIPv4Address(virtualIp) + " changed to " + IPv4.fromIPv4Address(mappedHostIp));
			System.out.println("Dest Mac " + MACAddress.valueOf(virtualMac).toString() + " changed to " + MACAddress.valueOf(mappedHostMac).toString());
			
			}
		}

	private void installSourceChangeRule(IOFSwitch currSw, int clientIp, int clientPort, byte[] clientMac, int virtualIp, int virtualPort, byte[] virtualMac, int mappedHostIp,
			byte[] mappedHostMac)
		{
		// Dest is virtual ip
		System.out.println("\nLB : Install Source Change Rule :  sw - " + String.valueOf(currSw.getId()));

		// Cnnxn rules - src ip, port, dest ip, port, Eth type, protocol
		// higher priority than the default (1). idle timeout of 20s

		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
		matchCriteria.setNetworkSource(mappedHostIp);
		matchCriteria.setTransportSource((short) virtualPort);
		matchCriteria.setNetworkDestination(clientIp);
		matchCriteria.setTransportDestination((short) clientPort);

		// Specifically for changing dest
		OFActionSetField act1 = new OFActionSetField();
		OFOXMField field1 = new OFOXMField(OFOXMFieldType.ETH_SRC, virtualMac);
		act1.setField(field1);

		OFActionSetField act2 = new OFActionSetField();
		OFOXMField field2 = new OFOXMField(OFOXMFieldType.IPV4_SRC, virtualIp);
		act2.setField(field2);

		List<OFAction> actionOps = new LinkedList<OFAction>();
		actionOps.add(act1);
		actionOps.add(act2);

		OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actionOps);
		OFInstructionGotoTable gotoTableInst = new OFInstructionGotoTable((byte) 1);

		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();

		instructionList.add(gotoTableInst);
		instructionList.add(applyActions);

		// install in this.table (0)
		SwitchCommands.removeRules(currSw, this.table, matchCriteria);
		if(SwitchCommands.installRule(currSw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 2), matchCriteria, instructionList, (short) 0, (short) 20) == false)
			{
			System.out.println("Rule not installed: sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		else
			{
			System.out.println("Rule installed : sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			System.out.println("Src IP " + IPv4.fromIPv4Address(mappedHostIp) + " changed to " + IPv4.fromIPv4Address(virtualIp));
			System.out.println("Src Mac " + MACAddress.valueOf(mappedHostMac).toString() + " changed to " + MACAddress.valueOf(virtualMac).toString());
			}
		}

	// Fwd to controller
	private void installFwdVirtualIpToControllerRule(IOFSwitch currSw, int targetIp)
		{
		System.out.println("\nLB Install Rule : Virt Ip Fwd to controller - sw - " + String.valueOf(currSw.getId()) + " for targetIp - " + IPv4.fromIPv4Address(targetIp));
		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkDestination(targetIp);

		OFActionOutput actOp = new OFActionOutput(OFPort.OFPP_CONTROLLER);
		List<OFAction> actionOps = new LinkedList<OFAction>();
		actionOps.add(actOp);
		OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actionOps);
		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();
		instructionList.add(applyActions);

		SwitchCommands.removeRules(currSw, this.table, matchCriteria);
		if(SwitchCommands.installRule(currSw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 1), matchCriteria, instructionList) == false)
			{
			System.out.println("LB : Virt Ip Fwd Ctrller  Rule not installed: sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		else
			{
			System.out.println("LB : Virt Ip Fwd Ctrller Rule installed : sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		}

	private void installFwdARPToControllerRule(IOFSwitch currSw, int targetIp)
		{
		System.out.println("\nLB : Install Rule :  Fwd to controller - sw - " + String.valueOf(currSw.getId()) + " for targetIp - " + IPv4.fromIPv4Address(targetIp));
		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkDestination(targetIp);
		matchCriteria.setNetworkProtocol((byte) Ethernet.TYPE_ARP);

		OFActionOutput actOp = new OFActionOutput(OFPort.OFPP_CONTROLLER);
		List<OFAction> actionOps = new LinkedList<OFAction>();
		actionOps.add(actOp);
		OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actionOps);
		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();
		instructionList.add(applyActions);

		SwitchCommands.removeRules(currSw, this.table, matchCriteria);
		if(SwitchCommands.installRule(currSw, this.table, (short) (SwitchCommands.DEFAULT_PRIORITY + 1), matchCriteria, instructionList) == false)
			{
			System.out.println("LB : Fwd ARP to ctrller Rule not installed: sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		else
			{
			System.out.println("LB : Fwd ARP to ctrller Rule installed : sw " + String.valueOf(currSw.getId()) + " table: " + this.table);
			}
		}

	/**
	 * Returns the MAC address for a host, given the host's IP address.
	 * 
	 * @param hostIPAddress
	 *            the host's IP address
	 * @return the hosts's MAC address, null if unknown
	 */
	private byte[] getHostMACAddress(int hostIPAddress)
		{
		Iterator<? extends IDevice> iterator = this.deviceProv.queryDevices(null, null, hostIPAddress, null, null);
		if(!iterator.hasNext())
			{
			return null;
			}
		IDevice device = iterator.next();
		return MACAddress.valueOf(device.getMACAddress()).toBytes();
		}

	/**
	 * Event handler called when a switch leaves the network.
	 * 
	 * @param DPID
	 *            for the switch
	 */
	@Override
	public void switchRemoved(long switchId)
		{ /* Nothing we need to do, since the switch is no longer active */
		}

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * 
	 * @param DPID
	 *            for the switch
	 */
	@Override
	public void switchActivated(long switchId)
		{ /* Nothing we need to do, since we're not switching controller roles */
		}

	/**
	 * Event handler called when a port on a switch goes up or down, or is added or removed.
	 * 
	 * @param DPID
	 *            for the switch
	 * @param port
	 *            the port on the switch whose status changed
	 * @param type
	 *            the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port, PortChangeType type)
		{ /* Nothing we need to do, since load balancer rules are port-agnostic */
		}

	/**
	 * Event handler called when some attribute of a switch changes.
	 * 
	 * @param DPID
	 *            for the switch
	 */
	@Override
	public void switchChanged(long switchId)
		{ /* Nothing we need to do */
		}

	/**
	 * Tell the module system which services we provide.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices()
		{
		return null;
		}

	/**
	 * Tell the module system which services we implement.
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls()
		{
		return null;
		}

	/**
	 * Tell the module system which modules we depend on.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies()
		{
		Collection<Class<? extends IFloodlightService>> floodlightService = new ArrayList<Class<? extends IFloodlightService>>();
		floodlightService.add(IFloodlightProviderService.class);
		floodlightService.add(IDeviceService.class);
		return floodlightService;
		}

	/**
	 * Gets a name for this module.
	 * 
	 * @return name for this module
	 */
	@Override
	public String getName()
		{
		return MODULE_NAME;
		}

	/**
	 * Check if events must be passed to another module before this module is notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name)
		{
		return(OFType.PACKET_IN == type && (name.equals(ArpServer.MODULE_NAME) || name.equals(DeviceManagerImpl.MODULE_NAME)));
		}

	/**
	 * Check if events must be passed to another module after this module has been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name)
		{
		return false;
		}
	}
