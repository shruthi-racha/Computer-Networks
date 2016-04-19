package edu.wisc.cs.sdn.apps.l3routing;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.wisc.cs.sdn.apps.util.Host;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.Link;

public class L3Routing implements IFloodlightModule, IOFSwitchListener, ILinkDiscoveryListener, IDeviceListener
	{
	public static final String MODULE_NAME = L3Routing.class.getSimpleName();

	// Interface to the logging system
	private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

	// Interface to Floodlight core for interacting with connected switches
	private IFloodlightProviderService floodlightProv;

	// Interface to link discovery service
	private ILinkDiscoveryService linkDiscProv;

	// Interface to device manager service
	private IDeviceService deviceProv;

	// Switch table in which rules should be installed
	public static byte table;

	// shru
	public int INFINITY = 1000;

	private static int debug_level = 2;

	// Map of hosts to devices
	private Map<IDevice, Host> knownHosts;

	// our
	HashMap<String, HashMap<String, Integer>> adjMatrix;

	/**
	 * Loads dependencies and initializes data structures.
	 */
	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException
		{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String, String> config = context.getConfigParams(this);
		table = Byte.parseByte(config.get("table"));

		this.floodlightProv = context.getServiceImpl(IFloodlightProviderService.class);
		this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
		this.deviceProv = context.getServiceImpl(IDeviceService.class);

		this.knownHosts = new ConcurrentHashMap<IDevice, Host>();
		}

	/**
	 * Subscribes to events and performs other startup tasks.
	 */
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException
		{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);

		this.adjMatrix = this.createAdjMatrix();
		/*********************************************************************/
		/* TODO: Initialize variables or perform startup tasks, if necessary */

		/*********************************************************************/
		}

	// shru
	/**
	 * Get the table in which this application installs rules.
	 */
	public byte getTable()
		{
		return this.table;
		}

	// shru

	/**
	 * Get a list of all known hosts in the network.
	 */
	private Collection<Host> getHosts()
		{
		return this.knownHosts.values();
		}

	/**
	 * Get a map of all active switches in the network. Switch DPID is used as the key.
	 */
	private Map<Long, IOFSwitch> getSwitches()
		{
		return floodlightProv.getAllSwitchMap();
		}

	/**
	 * Get a list of all active links in the network.
	 */
	private Collection<Link> getLinks()
		{
		return linkDiscProv.getLinks().keySet();
		}

	/**
	 * Event handler called when a host joins the network.
	 * 
	 * @param device
	 *            information about the host
	 */
	@Override
	public void deviceAdded(IDevice device)
		{
		Host host = new Host(device, this.floodlightProv);
		// We only care about a new host if we know its IP
		if(host.getIPv4Address() != null)
			{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);

			/*****************************************************************/
			/* TODO: Update routing: add rules to route to new host */
			if(host.isAttachedToSwitch() != false)
				{
				// TODO : adbhat : install rules to route traffic to this host

				updateAllRouting();

				// else nothing

				/*****************************************************************/
				}
			}
		}

	private void updateAllRouting()
		{
		if(debug_level < 2)
			System.out.println("\n**************Entering Update all Routing..**********************");

		this.adjMatrix = null;
		this.adjMatrix = this.createAdjMatrix();

		for (Host host : this.getHosts())
			{
			// find shortest path to host - come from BF
			HashMap<String, String> predecessors = new HashMap<String, String>();
			HashMap<String, Integer> fwdingToSrcPorts = new HashMap<String, Integer>();
			HashMap<String, Integer> distances = new HashMap<String, Integer>();
			bellmanFord(adjMatrix, host, predecessors, fwdingToSrcPorts, distances);

			for (Host otherHost : this.getHosts())
				{
				// foreach otherhost,
				if(debug_level < 2)
					System.out.println("Tracing path from host : " + otherHost.getName() + " to " + host.getName());
				if(otherHost == host)
					{
					continue;
					}
				IOFSwitch currSw = otherHost.getSwitch();
				if(currSw == null)
					{
					if(debug_level < 2)
						System.out.println("Host " + otherHost.getName() + " not connected to any switch");
					continue;
					}

				String currSwId;
				// for each switch in the path to this host
				while (currSw != null)
					{
					// install rule on currSw
					if(debug_level < 1)
						System.out.println("currSw: " + String.valueOf(currSw.getId()));
					currSwId = String.valueOf(currSw.getId());
					if(fwdingToSrcPorts.get(currSwId) == null)
						{
						if(debug_level < 1)
							System.out.println("currSw " + String.valueOf(currSw.getId()) + " has no further fwdToSrcPort entry");
						break;
						}
					int opPortTowardsSrc = fwdingToSrcPorts.get(currSwId);
					installL3FwdRule(currSw, host, opPortTowardsSrc);

					// currSw = predecessor.get(currSw);
					currSwId = predecessors.get(String.valueOf(currSw.getId()));
					if(currSwId.equals(host.getName()))
						{
						if(debug_level < 1)
							System.out.println("currSw " + String.valueOf(currSw.getId()) + " has no further predecessor, directly connected to host");
						break;
						}
					currSw = this.getSwitches().get(Long.parseLong(currSwId));
					if(currSw == null)
						{
						if(debug_level < 1)
							System.out.println("end of path since updated currSw for" + currSwId + " is null");
						}
					}
				}
			}
		if(debug_level < 2)
			displayAdjMatrix();
		}

	private void installL3FwdRule(IOFSwitch currSw, Host host, int opPortTowardsSrc)
		{
		if(debug_level < 2)
			System.out.println("L3 Install Rule :  sw - " + String.valueOf(currSw.getId()) + " port " + opPortTowardsSrc);
		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkDestination(host.getIPv4Address());

		List<OFAction> actionOps = new LinkedList<OFAction>();
		OFActionOutput actOp = new OFActionOutput(opPortTowardsSrc);
		actionOps.add(0, actOp);
		OFInstructionApplyActions applyActions = new OFInstructionApplyActions(actionOps);
		List<OFInstruction> instructionList = new ArrayList<OFInstruction>();
		instructionList.add(applyActions);

		SwitchCommands.removeRules(currSw, this.table, matchCriteria);
		if(SwitchCommands.installRule(currSw, this.table, SwitchCommands.DEFAULT_PRIORITY, matchCriteria, instructionList) == false)
			{
			System.out.println("L3 Rule not installed: sw " + String.valueOf(currSw.getId()) + " port: " + opPortTowardsSrc + " table: " + this.table);
			}
		else
			{
			if(debug_level < 2)
				System.out.println("L3 Rule installed : sw " + String.valueOf(currSw.getId()) + " port: " + opPortTowardsSrc + " table: " + this.table);
			}

		}

	public void bellmanFord(HashMap<String, HashMap<String, Integer>> adjMatrix, Host src, HashMap<String, String> predecessors, HashMap<String, Integer> fwdingToSrcPorts,
			HashMap<String, Integer> distances)
		{
		if(src.getSwitch() == null)
			return;
		for (Host host : this.getHosts())
			{
			distances.put(host.getName(), this.INFINITY);
			if(host.getSwitch() != null)
				{
				// predecessors.put(host.getName(), host.getSwitch().getStringId());
				// fwdingToSrcPorts.put(host.getName(), host.getPort());
				}
			}
		for (IOFSwitch sw : this.getSwitches().values())
			{
			String swIdName = String.valueOf(sw.getId());
			distances.put(swIdName, this.INFINITY);
			}
		distances.put(src.getName(), 0);
		predecessors.put(src.getName(), String.valueOf(src.getSwitch().getId()));
		// fwdingToSrcPorts.put(src.getName(), src.getPort());

		Set<String> allDeviceNames = this.adjMatrix.keySet();
		if(debug_level < 1)
			{
			System.out.println("BF : adjMetrix keyset size : " + allDeviceNames.size());
			System.out.println("Num Links : " + this.getLinks().size());
			}
		for (int i = 0;i < allDeviceNames.size();i++)
			{
			// for each u, v
			for (String device : allDeviceNames)
				{
				for (Map.Entry<String, Integer> adjEntry : adjMatrix.get(device).entrySet())
					{
					// for each v adjacent to u
					if(adjEntry.getValue() == 1) // 1 only if it is an edge
						{
						if((distances.get(device) + 1) < distances.get(adjEntry.getKey()))
							{
							distances.put(adjEntry.getKey(), (distances.get(device) + 1));
							predecessors.put(adjEntry.getKey(), device);

							// fwding ports need to be populated only if it is a switch
							// case 1 : this switch's predecessor is the src
							if(device.equals(src.getName()))
								{
								fwdingToSrcPorts.put(adjEntry.getKey(), src.getPort());
								}
							// case 2 : this switch's predecessor is another switch
							else
								{
								for (Link link : this.getLinks())
									{
									if(String.valueOf(link.getDst()).equals(device))
										{
										if(String.valueOf(link.getSrc()).equals(adjEntry.getKey()))
											{
											fwdingToSrcPorts.put(adjEntry.getKey(), link.getSrcPort());
											}
										}
									}
								}
							}
						}
					}
				}
			}
		if(debug_level < 2)
			displayBellmanFord(src, predecessors, fwdingToSrcPorts, distances);
		}

	public void bellmanFordOld(HashMap<String, HashMap<String, Integer>> adjMatrix, Host src, HashMap<String, String> predecessors, HashMap<String, Integer> fwdingToSrcPorts,
			HashMap<String, Integer> distances)
		{

		if(src.getSwitch() == null)
			return;
		for (Host host : this.getHosts())
			{
			distances.put(host.getName(), this.INFINITY);
			if(host.getSwitch() != null)
				{
				predecessors.put(host.getName(), String.valueOf(host.getSwitch().getId()));
				fwdingToSrcPorts.put(host.getName(), host.getPort());
				}
			}
		for (long swId : this.getSwitches().keySet())
			{
			String swIdName = String.valueOf(swId);
			distances.put(swIdName, this.INFINITY);
			}
		distances.put(src.getName(), 0);

		predecessors.put(src.getName(), String.valueOf(src.getSwitch().getId()));
		fwdingToSrcPorts.put(src.getName(), src.getPort());

		Set<String> allDeviceNames = this.adjMatrix.keySet();

		for (int i = 0;i < allDeviceNames.size() - 1;i++)
			{
			// for each u
			// for (Link link : this.getLinks())
			// {
			// System.out.println("Link Src: " + link.getSrc() + "\t" + link.getDst());
			// if(distances.get(String.valueOf(link.getSrc())) + 1 < distances.get(String.valueOf(link.getDst())))
			// {
			// distances.put(String.valueOf(link.getDst()), distances.get(String.valueOf(link.getSrc())) + 1);
			// predecessors.put(String.valueOf(link.getDst()), String.valueOf(link.getSrc()));
			// fwdingToSrcPorts.put(String.valueOf(link.getDst()), link.getDstPort());
			// }
			// }

			}
		if(debug_level < 2)
			displayBellmanFord(src, predecessors, fwdingToSrcPorts, distances);
		}

	private void displayAdjMatrix()
		{
		try
			{
			System.out.println("\nADJ MATRIX : ");
			for (Entry<String, HashMap<String, Integer>> row : this.adjMatrix.entrySet())
				{
				System.out.print(row.getKey() + " : ");
				for (Entry<String, Integer> col : row.getValue().entrySet())
					{
					System.out.print("(" + col.getKey() + "," + col.getValue() + ") ");
					}
				System.out.println();
				}
			}
		catch(Exception e)
			{
			e.printStackTrace();
			}
		}

	private void displayBellmanFord(Host src, HashMap<String, String> predecessors, HashMap<String, Integer> fwdingToSrcPorts, HashMap<String, Integer> distances)
		{
		try
			{
			System.out.println("\nBELLMAN FORD : src : " + src.getName() + " " + IPv4.fromIPv4Address(src.getIPv4Address()));
			System.out.print("Predecessors : ");
			for (Entry<String, String> predMapEntry : predecessors.entrySet())
				{
				System.out.print("(" + predMapEntry.getKey() + "," + predMapEntry.getValue() + ") ");
				}
			System.out.println();
			System.out.print("FwdPorts : ");
			for (Entry<String, Integer> mapEntry : fwdingToSrcPorts.entrySet())
				{
				System.out.print("(" + mapEntry.getKey() + "," + mapEntry.getValue() + ") ");
				}
			System.out.println();
			System.out.print("Distances : ");
			for (Entry<String, Integer> mapEntry : distances.entrySet())
				{
				System.out.print("(" + mapEntry.getKey() + "," + mapEntry.getValue() + ") ");
				}
			System.out.println();
			}
		catch(Exception e)
			{
			e.printStackTrace();
			}

		}

	public HashMap<String, HashMap<String, Integer>> createAdjMatrix()
		{
		HashMap<String, HashMap<String, Integer>> adjMatrix = new HashMap<String, HashMap<String, Integer>>();

		List<String> allDeviceNames = new ArrayList<String>();

		for (Host host : this.getHosts())
			{
			allDeviceNames.add(host.getName());
			}
		for (IOFSwitch sw : this.getSwitches().values())
			{
			allDeviceNames.add(String.valueOf(sw.getId()));
			}

		// use list of both switch names and host names to create table
		for (String hostName : allDeviceNames)
			{
			adjMatrix.put(hostName, new HashMap<String, Integer>());
			}

		for (HashMap<String, Integer> internalMap : adjMatrix.values())
			{
			for (String hostName : allDeviceNames)
				{
				internalMap.put(hostName, 0);
				}
			}

		// Insert real values of 1s

		for (Host host : this.getHosts())
			{
			if(host.getSwitch() != null)
				{
				String switchName = String.valueOf(host.getSwitch().getId());
				Map<String, Integer> internalMap;

				internalMap = adjMatrix.get(host.getName());
				internalMap.put(switchName, 1);

				internalMap = adjMatrix.get(switchName);
				internalMap.put(host.getName(), 1);
				}
			}

		for (Link link : this.getLinks())
			{
			if(debug_level < 2)
				System.out.println("Link src: " + link.getSrc() + " dest: " + link.getDst());
			String switchId1 = String.valueOf(link.getSrc());
			String switchId2 = String.valueOf(link.getDst());
			if(adjMatrix.get(switchId1)!=null)
				{
				adjMatrix.get(switchId1).put(switchId2, 1);
				}
			else
				{
				System.out.println("Looks like s"+switchId1+" is no longer up");
				}
			if(adjMatrix.get(switchId2)!=null)
				adjMatrix.get(switchId2).put(switchId1, 1);
			else
				System.out.println("Looks like s"+switchId2+" is no longer up");
			}
		return adjMatrix;
		}

	/**
	 * Event handler called when a host is no longer attached to a switch.
	 * 
	 * @param device
	 *            information about the host
	 */
	@Override
	public void deviceRemoved(IDevice device)
		{
		Host host = this.knownHosts.get(device);
		if(null == host)
			{
			return;
			}
		this.knownHosts.remove(host);

		log.info(String.format("Host %s is no longer attached to a switch", host.getName()));

		/*********************************************************************/
		/* TODO: Update routing: remove rules to route to host */

		OFMatch matchCriteria = new OFMatch();
		matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
		matchCriteria.setNetworkDestination(host.getIPv4Address());

		for (IOFSwitch currSw : this.getSwitches().values())
			{
			SwitchCommands.removeRules(currSw, this.table, matchCriteria);
			}
		/*********************************************************************/
		}

	/**
	 * Event handler called when a host moves within the network.
	 * 
	 * @param device
	 *            information about the host
	 */
	@Override
	public void deviceMoved(IDevice device)
		{
		Host host = this.knownHosts.get(device);
		if(null == host)
			{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
			}

		if(!host.isAttachedToSwitch())
			{
			this.deviceRemoved(device);
			return;
			}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(), host.getSwitch().getId(), host.getPort()));

		/*********************************************************************/
		/* TODO: Update routing: change rules to route to host */
		updateAllRouting();

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
		/* TODO: Update routing: change routing rules for all hosts */
		updateAllRouting();
		/*********************************************************************/
		}

	/**
	 * Event handler called when a switch leaves the network.
	 * 
	 * @param DPID
	 *            for the switch
	 */
	@Override
	public void switchRemoved(long switchId)
		{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d removed", switchId));

		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts */
		// check
		for (Host host : this.getHosts())
			{
			OFMatch matchCriteria = new OFMatch();

			matchCriteria.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			matchCriteria.setNetworkDestination(host.getIPv4Address());

			for (IOFSwitch currSw : this.getSwitches().values())
				{
				SwitchCommands.removeRules(currSw, this.table, matchCriteria);
				}
			updateAllRouting();
			}
		/*********************************************************************/
		}

	/**
	 * Event handler called when multiple links go up or down.
	 * 
	 * @param updateList
	 *            information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList)
		{
		for (LDUpdate update : updateList)
			{
			// If we only know the switch & port for one end of the link, then
			// the link must be from a switch to a host
			if(0 == update.getDst())
				{
				log.info(String.format("Link s%s:%d -> host updated", update.getSrc(), update.getSrcPort()));
				}
			// Otherwise, the link is between two switches
			else
				{
				log.info(String.format("Link s%s:%d -> s%s:%d updated", update.getSrc(), update.getSrcPort(), update.getDst(), update.getDstPort()));
				}
			}

		/*********************************************************************/
		/* TODO: Update routing: change routing rules for all hosts */
		updateAllRouting();

		/*********************************************************************/
		}

	/**
	 * Event handler called when link goes up or down.
	 * 
	 * @param update
	 *            information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update)
		{
		this.linkDiscoveryUpdate(Arrays.asList(update));
		}

	/**
	 * Event handler called when the IP address of a host changes.
	 * 
	 * @param device
	 *            information about the host
	 */
	@Override
	public void deviceIPV4AddrChanged(IDevice device)
		{
		this.deviceAdded(device);
		}

	/**
	 * Event handler called when the VLAN of a host changes.
	 * 
	 * @param device
	 *            information about the host
	 */
	@Override
	public void deviceVlanChanged(IDevice device)
		{ /* Nothing we need to do, since we're not using VLANs */
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
		{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */
		}

	/**
	 * Gets a name for this module.
	 * 
	 * @return name for this module
	 */
	@Override
	public String getName()
		{
		return this.MODULE_NAME;
		}

	/**
	 * Check if events must be passed to another module before this module is notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name)
		{
		return false;
		}

	/**
	 * Check if events must be passed to another module after this module has been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name)
		{
		return false;
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
		floodlightService.add(ILinkDiscoveryService.class);
		floodlightService.add(IDeviceService.class);
		return floodlightService;
		}

	}
