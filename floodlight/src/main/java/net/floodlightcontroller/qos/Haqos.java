
/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.haqos;

import java.io.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticflowentry.web.StaticFlowEntryWebRoutable;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.haqos.web.HaqosWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.UpdateOperation;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.OFPhysicalPort.PortSpeed;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@LogMessageCategory("HA QoS")
/**
 * This module is responsible for QoS and HA for cloud apps using OVS
 * switches. This is just a big 'ol dumb list of flows and something external
 * is responsible for ensuring they make sense for the network.
 */


public class Haqos
    implements IFloodlightModule, IHaqosService,
               IOFMessageListener, ITopologyListener {
    protected static Logger log = LoggerFactory.getLogger(Haqos.class);

    protected static boolean useAggrRsvpWithDscp = true;
    protected static long defaultDscpBandwidth = 60000000;

    public static final String MODULE_NAME = "haqos";

    public static final int HAQOS_APP_ID = 30;
    public static final short HAQOS_DEFAULT_IDLE_TIMEOUT = 30;
    static {
        AppCookie.registerApp(HAQOS_APP_ID, MODULE_NAME);
    }


    //protected ILinkDiscoveryService linkDiscovery;
    //protected IThreadPoolService threadPool;
    protected IFloodlightProviderService floodlightProvider;
    protected ITopologyService topology;
    protected IRoutingService routingEngine;
    protected IRestApiService restApi;

    protected Map<String /*PortName*/, String /*qosId*/ > qosMap;

    protected Map<String /*PortName*/,
              List<String> /*List of queueId*/ > queueMap;

    protected Map<String /*PortName*/,
              Long /*total port reservation*/ > portBandwidth;
    protected ConcurrentHashMap<String /*PortName*/,
              Long /*total port reservation*/ > portInterBandwidth;

    protected ConcurrentHashMap<RouteId, Route> routeMap;
    protected ConcurrentHashMap<RouteId, Short> sourcePortMap;
    protected ConcurrentHashMap<RouteId, Short> destPortMap;

    protected ConcurrentHashMap<RouteId, Long> bandwidthMap;

    protected ConcurrentHashMap<String /*Port Name*/,
              List<RouteId> /*List of routes associated*/ > portRouteMap;

    protected ConcurrentHashMap<NodePortTuple /*Port Id and switch id*/,
              String /*portName*/ > portNameMap;

    protected ConcurrentHashMap<Long /*Switch DpId*/, Boolean /*Active or not*/> switchMap;
    protected ConcurrentHashMap<Long /*Switch DpId*/, List<Short> /*ports on switch*/> switchPortMap;
    protected ConcurrentHashMap<LDUpdate , Boolean /*Active or not*/> portQueueMap;


    @Override
    public void printTest() {
    }


    @Override
    public void topologyChanged(List<LDUpdate> appliedUpdates) {


        if (useAggrRsvpWithDscp == true) {
            createQueuesOnSwitchConnect (appliedUpdates);
            return;
        }

        for (RouteId routeId : routeMap.keySet()) {
            Route route = routeMap.get(routeId);
            short srcPort = sourcePortMap.get(routeId).shortValue();
            short dstPort = destPortMap.get(routeId).shortValue();
            Route newRoute = routingEngine.getRoute(routeId.getSrc(), srcPort,
                                    routeId.getDst(), dstPort, 0);
            if (newRoute == null && route != null) {
                List<NodePortTuple> oldSwitches = route.getPath();
                for (NodePortTuple nodes : oldSwitches) {
                    Long switchDpId = new Long(nodes.getNodeId());
                    long currentBandwidth = 0;
                    synchronized (portBandwidth) {
                        currentBandwidth = portBandwidth.get(nodes.getPortId()).longValue();
                    }
                    long updatedBandwidth = currentBandwidth -
                            bandwidthMap.get(routeId).longValue();
                    updateQueueOnPort(nodes, updatedBandwidth);
                }
                continue;
            }
            if (newRoute.getId().getSrc() != route.getId().getSrc() &&
                newRoute.getId().getDst() != route.getId().getDst()) {
                /*Need to update the queues*/
                List<NodePortTuple> newSwitches = newRoute.getPath();
                HashSet<Long> dpIdSet = new HashSet<Long>();
                HashSet<Long> oldIdSet = new HashSet<Long>();
                for (NodePortTuple nodes : newSwitches) {
                    long switchDpId = nodes.getNodeId();
                    dpIdSet.add(new Long(switchDpId));
                }

                List<NodePortTuple> oldSwitches = route.getPath();
                for (NodePortTuple nodes : oldSwitches) {
                    Long switchDpId = new Long(nodes.getNodeId());
                    oldIdSet.add(switchDpId);
                    if (dpIdSet.contains(switchDpId) == false) {
                        long currentBandwidth = 0;
                        synchronized (portBandwidth) {
                            currentBandwidth =
                                portBandwidth.get(nodes.getPortId()).longValue();
                        }
                        long updatedBandwidth = currentBandwidth -
                            bandwidthMap.get(routeId).longValue();
                        updateQueueOnPort(nodes, updatedBandwidth);
                    }
                }
                long useBandwidth = bandwidthMap.get(routeId).longValue();
                long bandwidth = useBandwidth;
                for (NodePortTuple nodes : newSwitches) {
                    long switchDpId = nodes.getNodeId();
                    Long dpId = new Long(switchDpId);
                    short portId = nodes.getPortId();
                    if (oldIdSet.contains(dpId)) {
                        continue;
                    }
                    IOFSwitch sw = floodlightProvider.getSwitch(switchDpId);
                    ImmutablePort port = sw.getPort(portId);
                    String portName = port.getName();
                    portNameMap.put(nodes, portName);
                    synchronized (nodes) {
                        if (portRouteMap.containsKey(portName)) {
                             useBandwidth = getUpdatedBandwidth(portName,
                                              newRoute.getId(), bandwidth);
                        } else {
                            List<RouteId> routes = new ArrayList<RouteId>();
                            routes.add(newRoute.getId());
                            portRouteMap.put(portName, routes);
                        }
                        synchronized (portBandwidth) {
                            portBandwidth.put(portName, useBandwidth);
                        }
                        //IOFSwitch sw = floodlightProvider.getSwitch(switchDpId);
                        //ImmutablePort port = sw.getPort(portId);
                        //String portName = port.getName();
                        String qosName = "qos" + portId;
                        String queueName = "qu" + portId;
                        String[] command = {"python",
                          "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/CreateQueues.py",
                          "--qName=" + queueName, "--srcPort=" + portName, "--qosName=" + qosName, "--qNum=" + portId,
                          "--bandwidth=" + useBandwidth};
                        callOvsUsingProcess(command, portName);
                    }
                }
                bandwidthMap.remove(routeId);
                bandwidthMap.put(newRoute.getId(), bandwidth);
                routeMap.remove(routeId);
                routeMap.put(newRoute.getId(), newRoute);
            }
        }
    }


    private void createQueuesOnSwitchConnect (List<LDUpdate> updates) {
        for (LDUpdate update : updates) {
            long src = update.getSrc();
            if (update.getOperation () == UpdateOperation.SWITCH_UPDATED) {
                log.info ("src switch " + src);
                log.info ("switch contains " + switchMap.containsKey(src));
                if (switchMap.containsKey(src) == false ||
                    (switchMap.containsKey(src) == true &&
                     switchMap.get(src).booleanValue() == false)) {
                    switchMap.put (src, true);
                    List<Short> ports = new ArrayList<Short> ();
                    switchPortMap.put (src, ports);
                    //createQueuesOnPorts(src);
                }
            }
            if (update.getOperation () == UpdateOperation.SWITCH_REMOVED) {
                if (switchMap.containsKey(src) == true) {
                    switchMap.put(src, false);
                    List<Short> ports = switchPortMap.get(src);
                    for (Short port : ports) {
                        short portId = port.shortValue();
                        NodePortTuple tuple =
                          new NodePortTuple (src, portId);
                        updateQueueOnPort (tuple, 0);
                        LDUpdate compUpdate =
                          new LDUpdate(src, portId, UpdateOperation.PORT_UP);
                        portQueueMap.remove (compUpdate);
                    }
                    switchPortMap.remove(src);
                }
            }
            if (update.getOperation () == UpdateOperation.PORT_UP) {
                short portId = update.getSrcPort();
                LDUpdate compUpdate =
                  new LDUpdate(src, portId, UpdateOperation.PORT_UP);
                if (portQueueMap.containsKey(compUpdate) == false ||
                    (portQueueMap.containsKey(compUpdate) == true &&
                     portQueueMap.get(compUpdate).booleanValue() == false)) {
                    switchMap.put (src, true);
                    List<Short> ports = switchPortMap.get(src);
                    portQueueMap.put (compUpdate, true);
                    createQueueOnPort (src, portId);
                    if (portQueueMap.containsKey(compUpdate) == false) {
                        ports.add(portId);
                        switchPortMap.put(src, ports);
                    }
                }
            }
            if (update.getOperation () == UpdateOperation.PORT_DOWN) {
                short portId = update.getSrcPort();
                LDUpdate compUpdate =
                  new LDUpdate(src, portId, UpdateOperation.PORT_UP);
                portQueueMap.put (compUpdate, false);
                NodePortTuple tuple =
                          new NodePortTuple (src, portId);
                updateQueueOnPort (tuple, 0);
            }
        }
    }


    private void createQueuesOnPorts (long src) {
        Set<Short> ports = topology.getPortsWithLinks (src);
        if (ports == null) {
            return;
        }
        for (Short port : ports) {
            createQueueOnPort (src, port.shortValue());
        }
    }


    private void createQueueOnPort (long src, short portId) {
        IOFSwitch sw = floodlightProvider.getSwitch(src);
        ImmutablePort immPort = sw.getPort(portId);
        String portName = immPort.getName();
        String qosName = "qos" + portId;
        String queueName = "qu" + portId;
        String[] command = {"python",
          "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/CreateEfQueue.py",
          "--qName=" + queueName, "--srcPort=" + portName, "--qosName=" + qosName, "--qNum=" + portId,
          "--bandwidth=" + defaultDscpBandwidth};
        synchronized (portBandwidth) {
            portBandwidth.put(portName, defaultDscpBandwidth);
        }
        log.info (" created port for " + portName);
        callOvsUsingProcess(command, portName);
    }

    private void updateQueueOnPort (NodePortTuple node, long bandwidth) {

            short portId = node.getPortId(); 
            String portName = portNameMap.get(node);
            String qosName;
            log.info(" at update queue " + portName);
            synchronized (qosMap) {
                if (qosMap.containsKey(portName)) {
                    qosName = qosMap.get(portName);
                    log.info(" removing " + qosName + " " + portName);
                    String[] command = {"python",
                        "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/DeleteQos.py",
                        "--qosName=" + qosName, "--portName=" + portName};

                    deleteQosUsingProcess(command);
                    qosMap.remove(portName);
                }
            }
            synchronized (queueMap) {
                if (queueMap.containsKey(portName)) {
                    List<String> queueNames = queueMap.get(portName);
                    if (queueNames != null) {
                        for (String queueName : queueNames) {
                            log.info(" removing " + queueName + " " + portName);

                            String [] cmd = {"python",
                              "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/DeleteQueue.py",
                              "--queueName=" + queueName};
                            deleteQosUsingProcess(cmd);
                        }
                    }
                    queueMap.remove(portName);
                }
            }

            if (bandwidth > 0) {
                qosName = "qos" + portId;
                String queueName = "qu" + portId;
                synchronized (portBandwidth) {
                    portBandwidth.put(portName, bandwidth);
                }
                log.info(" adding " + queueName + " " + portName + " bandwidth " + bandwidth);
                String[] addCmd = {"python",
                      "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/CreateQueues.py",
                      "--qName=" + queueName, "--srcPort=" + portName, "--qosName=" + qosName, "--qNum=" + portId,
                      "--bandwidth=" + bandwidth};
                callOvsUsingProcess(addCmd, portName);
            } else {
                synchronized (portBandwidth) {
                    portBandwidth.remove(portName);
                }
            }
    }


    @Override
    public List <OFStatistics> getQueuesOnSwitch(long switchId) {
        
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        Future<List<OFStatistics>> future;
        List<OFStatistics> values = null;
        if (sw != null) {
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(OFStatisticsType.QUEUE);
            int reqLength = req.getLengthU();
            OFQueueStatisticsRequest specReq = new OFQueueStatisticsRequest();
            specReq.setPortNumber(OFPort.OFPP_ALL.getValue());
            specReq.setQueueId(0xffffffff);
            req.setStatistics(Collections.singletonList((OFStatistics)specReq));
            reqLength += specReq.getLength();
            req.setLengthU(reqLength);
            try {
                future = sw.queryStatistics(req);
                values = future.get(10, TimeUnit.SECONDS);
                log.info("after getting values");
            } catch (Exception e) {
                log.error("Failure retrieving queues from switch " + sw, e);
            }
        }
        return values;
    }

    @Override
    public boolean hasBandwidthOnPath(long srcId, long dstId, long bandwidth) {

        Route route = routingEngine.getRoute(srcId, dstId, 0);
        List<NodePortTuple> switches = route.getPath();
        for (NodePortTuple node : switches) {
            short portId = node.getPortId();
            long dpId = node.getNodeId();
            IOFSwitch sw = floodlightProvider.getSwitch(dpId);
            ImmutablePort port = sw.getPort(portId);
            long speedInBps = port.getCurrentPortSpeed().getSpeedBps();
            if (speedInBps == 0) {
                log.info("port has 0 speed, setting 1000");
                speedInBps = 1000000;
            }
            long availableBandwidth = speedInBps - getUsedBandwidth(port.getName());
            if (availableBandwidth < bandwidth) {
              return false;
            }
        }
        return true;
    }


    @Override
    public boolean reserveInterBandwidth(long srcId, String srcPort, long dstId,
                    String dstPort, long bandwidth, short tpSrc, String srcIp) {
        
        IOFSwitch sw = floodlightProvider.getSwitch(srcId);
        ImmutablePort port = sw.getPort(srcPort);
        short srcPortNum = port.getPortNumber();

        IOFSwitch dstSw = floodlightProvider.getSwitch(dstId);
        ImmutablePort dstImmPort = dstSw.getPort(dstPort);
        short dstPortNum = dstImmPort.getPortNumber();

        Route route =
                routingEngine.getRoute(srcId, srcPortNum, dstId, dstPortNum, 0);
        List<NodePortTuple> switches = route.getPath();
        int i = 0;
        for (NodePortTuple node : switches) {
            if ((i % 2) == 0) {
                i += 1;
                continue;
            }
            i += 1;
            short portId = node.getPortId();
            long dpId = node.getNodeId();
            sw = floodlightProvider.getSwitch(dpId);
            port = sw.getPort(portId);
            String portName = port.getName();
            portNameMap.put (node, portName); 
            long speedInBps = port.getCurrentPortSpeed().getSpeedBps();
            if (speedInBps == 0) {
                speedInBps = 100000000;
            }
            long dscpBandwidth = defaultDscpBandwidth;
            synchronized (portBandwidth) {
                if (portBandwidth.containsKey (portName) == false) {
                    createQueueOnPort (dpId, portId);
                    portBandwidth.put(portName, defaultDscpBandwidth);
                }
                dscpBandwidth = portBandwidth.get(portName);
            }

            long alreadyRsvd = 0;
            if (portInterBandwidth.containsKey(portName)) {
                alreadyRsvd = portInterBandwidth.get(portName);
            }
            
            portInterBandwidth.put(portName, alreadyRsvd + bandwidth);
            log.info (" resv for " + dpId + " port " + portId + " already rsvd " + alreadyRsvd + " bandwidth " + bandwidth);
            if ((dscpBandwidth - alreadyRsvd) < bandwidth) {
                if (speedInBps < (alreadyRsvd + bandwidth)) {
                    return false;
                }
                log.info (" increasing resv for " + dpId + " port " + portId + " new bw " + (alreadyRsvd + bandwidth));
                updateQueueOnPort (node, alreadyRsvd + bandwidth);
                synchronized (portBandwidth) {
                    portBandwidth.put(portName, alreadyRsvd + bandwidth);
                }
            }
        }
        addFlowWithoutTunnel (switches, tpSrc, srcIp, 1); 
        return true;
    }



    private void addFlowForEfQ (List<NodePortTuple> switches,
                                short tpSrc, short tpDst) {
        int i = 0;
        int size = switches.size();
        NodePortTuple start = switches.get(0);
        long dpId = start.getNodeId();
        short portId = start.getPortId();
        IOFSwitch sw = floodlightProvider.getSwitch(dpId);

        for (i = 0; i < size; i += 2) {
            NodePortTuple node = switches.get(i);

            dpId = node.getNodeId();
            portId = node.getPortId();
            sw = floodlightProvider.getSwitch(dpId);
            OFMatch match = new OFMatch();
            int wildCards = OFMatch.OFPFW_ALL &
                ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_DL_TYPE;
            if (tpSrc != -1) {
                wildCards = wildCards & ~OFMatch.OFPFW_TP_SRC;
            }
            if (tpDst != -1) {
                wildCards = wildCards & ~OFMatch.OFPFW_TP_DST;
            }
            
            match.setWildcards(wildCards);
            match.setDataLayerType((short) 0x0800);
            match.setNetworkProtocol((byte) 6);
            if (tpSrc != -1) {
                match.setTransportSource(tpSrc);
            }
            if (tpDst != -1) {
                match.setTransportDestination(tpDst);
            }
            OFFlowMod fm = new OFFlowMod ();
            List<OFAction> actions = new LinkedList<OFAction>();
            OFActionEnqueue action = new OFActionEnqueue ();
            action.setPort(portId);
            // We have portId and QueueId same. check the place where
            // queues are created.
            action.setQueueId(portId);
            actions.add(action);
            long cookie = AppCookie.makeCookie(HAQOS_APP_ID, 0);
            fm.setMatch(match)
              .setActions(actions)
              .setCommand(OFFlowMod.OFPFC_ADD)
              .setCookie(cookie)
              .setPriority(Short.MAX_VALUE)
              .setBufferId(OFPacketOut.BUFFER_ID_NONE)
              .setLengthU(OFFlowMod.MINIMUM_LENGTH +
                          OFActionEnqueue.MINIMUM_LENGTH);
            try {
                sw.write(fm, null);
                sw.flush();
            } catch (IOException e) {
                log.info (" Error adding flow to direct the flow to queue");
            }
       }
    }


    @Override
    public boolean createEfQueue(long srcId, long dstId,
        long bandwidth, short tpSrc, short tpDst) {
        
        Route route =
                routingEngine.getRoute(srcId, dstId, 0);
        List<NodePortTuple> switches = route.getPath();
        int i = 0;
        for (NodePortTuple node : switches) {
            if ((i % 2) != 0) {
                i += 1;
                continue;
            }
            i += 1;
            short portId = node.getPortId();
            long dpId = node.getNodeId();
            IOFSwitch sw = floodlightProvider.getSwitch(dpId);
            ImmutablePort port = sw.getPort(portId);
            log.info(" switchId " + dpId + " portId " + portId);
            String portName = port.getName();
            String qosName = "qos" + portId;
            String queueName = "qu" + portId;
            String[] command = {"python",
                      "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/CreateEfQueue.py",
                      "--qName=" + queueName, "--srcPort=" + portName, "--qosName=" + qosName, "--qNum=" + portId,
                      "--bandwidth=" + bandwidth};
            callOvsUsingProcess(command, portName);
        }
        addFlowForEfQ(switches, tpSrc, tpDst);
        return true;
    }

    private long getUsedBandwidth (String portName) {
        long usedBandwidth = 0;
        if (portRouteMap.containsKey(portName)) {
            List<RouteId> routeIds = portRouteMap.get(portName);
            for (RouteId routeId : routeIds) {
                if (bandwidthMap.containsKey(routeId)) {
                    usedBandwidth += bandwidthMap.get(routeId);
                }
            }
        }
        return usedBandwidth;
    }



    private void deleteQosUsingProcess(String[] command) {

        try {
            log.info(" before start ");
            Process cmdProcess = new ProcessBuilder(command).start();
            log.info(" after start ");
        } catch (IOException e) {
            log.info("io exception ");
        }
    }

    private void addTunnelFlowWithTos(String switchName, short tcpPort,
        short inPort, short outPort, int dscpValue) {

        String[] command = {"python",
              "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/AddTunnelFlow.py",
              "--tunId=0x2", "--inPort=" + inPort, "--tcpPort=" + tcpPort,
              "--outPort=" + outPort, "--dscpVal=" + dscpValue, "--brName=" + switchName};
        try {
            log.info(" before start ");
            Process cmdProcess = new ProcessBuilder(command).start();
            log.info(" after start ");
        } catch (IOException e) {
            log.info("io exception ");
        }
    }


    private boolean isTunnelPort(String[] command) {

        try {
            log.info(" before start ");
            Process cmdProcess = new ProcessBuilder(command).start();
            log.info(" after start ");
            InputStream inputStream = cmdProcess.getInputStream();
            InputStreamReader inputReader = new InputStreamReader(inputStream);
            BufferedReader bufReader = new BufferedReader(inputReader);
            String line;
            int i = 0;
            line = bufReader.readLine();
            return (Integer.parseInt(line) == 1);
        } catch (IOException e) {
            log.info("io exception ");
        }
        return false;
    }


    private List<String> getRemoteAndLocalIp(String portName) {

        List<String> ipStrings = new ArrayList<String>();
        String[] command = {"python",
              "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/GetRemoteIp.py",
              "--intName=" + portName};
        try {
            log.info(" before start ");
            Process cmdProcess = new ProcessBuilder(command).start();
            log.info(" after start ");
            InputStream inputStream = cmdProcess.getInputStream();
            InputStreamReader inputReader = new InputStreamReader(inputStream);
            BufferedReader bufReader = new BufferedReader(inputReader);
            String line;
            int i = 0;
            line = bufReader.readLine();
            ipStrings.add(line);
            line = bufReader.readLine();
            ipStrings.add(line);
            return ipStrings;
        } catch (IOException e) {
            log.info("io exception ");
        }
        return ipStrings;
    }




    private void callOvsUsingProcess(String[] command, String srcPort) {

        try {
            Process cmdProcess = new ProcessBuilder(command).start();
            InputStream inputStream = cmdProcess.getInputStream();
            InputStreamReader inputReader = new InputStreamReader(inputStream);
            BufferedReader bufReader = new BufferedReader(inputReader);
            String line;
            int i = 0;
            List<String> qidList = new ArrayList<String> ();
            while ((line = bufReader.readLine()) != null) {
                if (line == "") {
                    continue;
                }
                if (i == 0) {
                    synchronized (qosMap) {
                        qosMap.put(srcPort, line);
                    }
                } else {
                    qidList.add(line);
                }
                i += 1;
            }
            synchronized (queueMap) {
                queueMap.put(srcPort, qidList);
            }
        } catch (IOException e) {
            log.info("io exception ");
        }
    }

    private long getUpdatedBandwidth(String portName, RouteId addRoute, long bandwidth)
    {
        long updatedBandwidth = bandwidth;
        synchronized (portRouteMap.get(portName)) {
            List<RouteId> routesOnPort = portRouteMap.get(portName);
            ListIterator<RouteId> iter = routesOnPort.listIterator();
            boolean alreadyAdded = false;
            while (iter.hasNext()) {
                RouteId routeId = iter.next();
                if (routeId == addRoute) {
                    alreadyAdded = true;
                    continue;
                }
                updatedBandwidth += bandwidthMap.get(routeId).longValue();
            }
            if (alreadyAdded == false) {
                routesOnPort.add(addRoute);
                portRouteMap.put(portName, routesOnPort);
            }
        }
        return updatedBandwidth;
    }


    private int getTunnelNode (List<NodePortTuple> switches, int index) {
        int size = switches.size();
        for (int i = index; i < (index + 3); i += 2) {
            NodePortTuple srcSwitch = switches.get(i);
            long dpId = srcSwitch.getNodeId();
            short portId = srcSwitch.getPortId();
            IOFSwitch sw = floodlightProvider.getSwitch(dpId);
            ImmutablePort port = sw.getPort(portId);
            String portName = port.getName();
            String[] command = {"python",
              "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/GetInterfaceType.py",
              "--intName=" + portName};
            if (isTunnelPort(command)) {
                log.info("has tunnel port");
                return i;
            } else {
                log.info("not tunnel port " + dpId);
            }
        }
        log.info("before returning -1");
        return -1;
    }

    private int getIpAddressInInt(InetAddress srcInet) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.SIZE/8);
        buffer.put (srcInet.getAddress());
        buffer.position(0);
        return buffer.getInt();
    }





    private void addFlowWithoutTunnel (List<NodePortTuple> switches,
                                        short tcpPort, String srcIp, int index) {
        int i = 0;
        int size = switches.size();
        NodePortTuple start = switches.get(0);
        long dpId = start.getNodeId();
        short portId = start.getPortId();
        IOFSwitch sw = floodlightProvider.getSwitch(dpId);

        //InetSocketAddress srcIp = (InetSocketAddress)sw.getInetAddress();
        //InetAddress srcInet = srcIp.getAddress();
        //int srcNetworkAddress = getIpAddressInInt(srcInet);
        // NodePortTuple List. at index 0: connection from VM.
        // At Index 1 connection from bridge to out.
        // At Index 2 connection from bridge to the 2nd switch.
        // So at index 3, 5, 7 ... we have the out ports in all
        // the links towards destination.
        for (i = index; i < size; i += 2) {
            NodePortTuple node = switches.get(i);
            NodePortTuple prevNode;
            short inputPort = 0;
            if (i > 0) {
                prevNode= switches.get(i-1);
                inputPort = prevNode.getPortId();
            }

            dpId = node.getNodeId();
            portId = node.getPortId();
            sw = floodlightProvider.getSwitch(dpId);
            OFMatch match = new OFMatch();
            int wildCards = OFMatch.OFPFW_ALL & /*~OFMatch.OFPFW_NW_SRC_MASK &*/
                /*~OFMatch.OFPFW_TP_SRC & ~OFMatch.OFPFW_IN_PORT &*/
                ~OFMatch.OFPFW_NW_PROTO & ~OFMatch.OFPFW_DL_TYPE;
            if (i > 0) {
                wildCards = wildCards & ~OFMatch.OFPFW_IN_PORT;
            }
            if (tcpPort != -1) {
                wildCards = wildCards & ~OFMatch.OFPFW_TP_SRC;
            }
            if (srcIp.equals("None") == false) {
                wildCards = wildCards & ~OFMatch.OFPFW_NW_SRC_MASK;
            }
            match.setWildcards(wildCards);
            match.setDataLayerType((short) 0x0800);
            match.setNetworkProtocol((byte) 6);
            if ( i > 0) {
                match.setInputPort(inputPort);
            }
            if (tcpPort != -1) {
                match.setTransportSource(tcpPort);
            }
            if (srcIp.equals("None") == false) {
                InetAddress srcIpInet = null;
                try {
                    srcIpInet = InetAddress.getByName(srcIp);
                } catch (UnknownHostException e) {
                    log.info("unknown host");
                }
                match.setNetworkSource(getIpAddressInInt(srcIpInet));
            }
            OFFlowMod fm = new OFFlowMod ();
            List<OFAction> actions = new LinkedList<OFAction>();
            OFActionEnqueue action = new OFActionEnqueue ();
            action.setPort(portId);
            // We have portId and QueueId same. check the place where
            // queues are created.
            action.setQueueId(portId);
            actions.add(action);
            long cookie = AppCookie.makeCookie(HAQOS_APP_ID, 0);
            fm.setMatch(match)
              .setActions(actions)
              .setCommand(OFFlowMod.OFPFC_ADD)
              .setCookie(cookie)
              .setPriority(Short.MAX_VALUE)
              .setBufferId(OFPacketOut.BUFFER_ID_NONE)
              .setLengthU(OFFlowMod.MINIMUM_LENGTH +
                          OFActionEnqueue.MINIMUM_LENGTH);
            try {
                sw.write(fm, null);
                sw.flush();
            } catch (IOException e) {
                log.info (" Error adding flow to direct the flow to queue");
            }
        }
    }


    private void addFlowWithTunnel (List<NodePortTuple> switches,
        short tcpPort, int tunnelStart, int tunnelEnd) {

        InetSocketAddress srcIp;
        InetAddress srcInet;
        int srcNetworkAddress;
        int i = 0;
        short inputPort=0;
        int remoteIp=0;
        int localIp=0;
        for (NodePortTuple node : switches) {
            if (i < tunnelStart || i >= tunnelEnd) {
                if (i == (tunnelStart -1)) {
                    inputPort = node.getPortId();
                }
                i += 1;
                continue;
            }
            long dpId = node.getNodeId();
            short portId = node.getPortId();
            IOFSwitch sw = floodlightProvider.getSwitch(dpId);
            // may also add destination tunnel ip.
            if (i == tunnelStart) {
                // get remote IP. The packet will be matched by the
                // local and remote IP as src and dest nw address.
                // add flow for pushing to queue in case of tunnelStart > 2
                ImmutablePort port = sw.getPort(portId);
                String portName = port.getName();
                ImmutablePort internalPort = sw.getPort((short) 65534);
                String switchName = port.getName();
                List<String> ipStrings = getRemoteAndLocalIp(portName);
                remoteIp = IPv4.toIPv4Address(ipStrings.get(0));
                localIp = IPv4.toIPv4Address(ipStrings.get(1));
                NodePortTuple prevNode = switches.get(i-1);
                short inPort = prevNode.getPortId();
                // Need to add flow to tunnel the packet, add DSCP bit on
                // the outer header and then queue the packet.
                // The OFAction does not currently have set_tunnel action
                // and hence using ovs-ofctl command.
                addTunnelFlowWithTos(switchName, tcpPort, portId, inPort, 32);
                i += 1;
                continue;
            }
            OFMatch match = new OFMatch();
            match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_NW_SRC_MASK &
                ~OFMatch.OFPFW_NW_DST_MASK & ~OFMatch.OFPFW_NW_TOS);
            match.setNetworkSource(localIp);
            match.setNetworkDestination(remoteIp);
            match.setNetworkTypeOfService((byte)32);
            //match.setTransportSource(tcpPort);
            OFFlowMod fm = new OFFlowMod ();
            List<OFAction> actions = new LinkedList<OFAction>();
            OFActionEnqueue action = new OFActionEnqueue ();
            action.setPort(portId);
            // We have portId and QueueId same. check the place where
            // queues are created.
            action.setQueueId(portId);
            actions.add(action);
            long cookie = AppCookie.makeCookie(HAQOS_APP_ID, 0);
            fm.setActions(actions)
              .setMatch(match)
              .setHardTimeout((short) 0)
              .setIdleTimeout((short) HAQOS_DEFAULT_IDLE_TIMEOUT)
              .setCommand(OFFlowMod.OFPFC_ADD)
              .setCookie(cookie)
              .setBufferId(OFPacketOut.BUFFER_ID_NONE)
              .setLengthU(OFFlowMod.MINIMUM_LENGTH +
                          OFActionEnqueue.MINIMUM_LENGTH);
            try {
                sw.write(fm, null);
                sw.flush();
            } catch (IOException e) {
                log.info (" Error adding flow to direct the flow to queue");
            }
            i += 1;
        }
    }



    private void addFlowsOnQueues (List<NodePortTuple> switches,
        short tcpPort, String srcIp) {
        int size = switches.size();
        int tunnelStart = getTunnelNode(switches, 1);
        log.info(" tunnel start " + tunnelStart);
        int tunnelEnd = 65536;
        if (tunnelStart != -1) {
            tunnelEnd = getTunnelNode(switches, size - 4);
        }
        int i = 0;
        if (tunnelStart == -1) {
            log.info(" calling without tunnel ");
            addFlowWithoutTunnel(switches, tcpPort, srcIp, 1);
        } else {
            addFlowWithTunnel(switches, tcpPort, tunnelStart, tunnelEnd);
        }
    }


    @Override
    public void createQueuesOnPath(long srcId, String srcPort,
        long dstId, String dstPort, long bandwidth,
        short tcpPort, String srcIp) {
        /* 
         * Create egress queue based on srcPort if srcId = dstId
         */
        IOFSwitch sw = floodlightProvider.getSwitch(srcId);
        ImmutablePort port = sw.getPort(srcPort);
        short portNum = port.getPortNumber();

        IOFSwitch dstSw = floodlightProvider.getSwitch(dstId);
        ImmutablePort dstImmPort = dstSw.getPort(dstPort);
        short dstPortNum = dstImmPort.getPortNumber();

        if (srcId == dstId) {
            String qosName = "qos" + portNum;
            String queueName = "qu" + portNum;
            String[] command = {"python",
                "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/CreateQueues.py",
                "--qName=" + queueName, "--srcPort=" + srcPort, "--qosName=" + qosName, "--qNum=" + portNum,
                "--bandwidth=" + bandwidth};

            callOvsUsingProcess(command, srcPort);
        } else {
            Route route =
                routingEngine.getRoute(srcId, portNum, dstId, dstPortNum, 0);
            List<NodePortTuple> switches = route.getPath();
            ListIterator<NodePortTuple> iter = switches.listIterator();
            int i = 0;
            log.info("else of create queue");
            if (switches != null && switches.isEmpty() == false) {
                log.info("switches are not null");
                routeMap.put(route.getId(), route);
                sourcePortMap.put(route.getId(), new Short(portNum));
                destPortMap.put(route.getId(), new Short(dstPortNum));
                bandwidthMap.put(route.getId(), new Long(bandwidth));
            }
            while(iter.hasNext()) {
                NodePortTuple tuple = iter.next();
                long switchId = tuple.getNodeId();
                long useBandwidth = bandwidth;
                short portId = tuple.getPortId();
                /*if ((switchId == srcId && portNum == portId) || switchId == dstId) {
                    continue;
                }*/
                if ((i % 2) == 0) {
                    i += 1;
                    continue;
                }
                i += 1;
                log.info (" switch id " + switchId + " portId " + portId);
                sw = floodlightProvider.getSwitch(switchId);
                port = sw.getPort(portId);
                String portName = port.getName();
                portNameMap.put (tuple, portName); 
                synchronized (tuple) {
                    if (portRouteMap.containsKey(portName)) {
                        useBandwidth = getUpdatedBandwidth(portName,
                                          route.getId(), bandwidth);
                    } else {
                        List<RouteId> routes = new ArrayList<RouteId>();
                        routes.add(route.getId());
                        portRouteMap.put(portName, routes);
                    }
                    synchronized (portBandwidth) {
                        portBandwidth.put(portName, useBandwidth);
                    }
                    String qosName = "qos" + portId;
                    String queueName = "qu" + portId;
                    String[] command = {"python",
                      "/home/snathan/floodlight-master/src/main/java/net/floodlightcontroller/haqos/CreateQueues.py",
                      "--qName=" + queueName, "--srcPort=" + portName, "--qosName=" + qosName, "--qNum=" + portId,
                      "--bandwidth=" + useBandwidth};
                    callOvsUsingProcess(command, portName);
                }
            }
            addFlowsOnQueues (switches, tcpPort, srcIp);

        }
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IHaqosService.class);
        return l;
    }


    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m =
            new HashMap<Class<? extends IFloodlightService>,
                IFloodlightService>();
        // We are the class that implements the service
        m.put(IHaqosService.class, this);
        return m;
    }


    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        //l.add(ILinkDiscoveryService.class);
        //l.add(IThreadPoolService.class);
        l.add(IFloodlightProviderService.class);
        l.add(ITopologyService.class);
        l.add(IRoutingService.class);
        l.add(IRestApiService.class);
        return l;
    }


    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        //linkDiscovery = context.getServiceImpl(ILinkDiscoveryService.class);
        //threadPool = context.getServiceImpl(IThreadPoolService.class);
        floodlightProvider =
                context.getServiceImpl(IFloodlightProviderService.class);
        topology = context.getServiceImpl(ITopologyService.class);
        routingEngine = context.getServiceImpl(IRoutingService.class);
        restApi = context.getServiceImpl(IRestApiService.class);

        qosMap = Collections.synchronizedMap (new HashMap<String, String> ());
        queueMap = Collections.synchronizedMap (new HashMap<String, List<String>> ());
        routeMap = new ConcurrentHashMap<RouteId, Route> ();
        sourcePortMap = new ConcurrentHashMap<RouteId, Short> ();
        destPortMap = new ConcurrentHashMap<RouteId, Short> ();
        bandwidthMap = new ConcurrentHashMap<RouteId, Long> ();
        portRouteMap = new ConcurrentHashMap<String, List<RouteId>> ();
        portBandwidth = Collections.synchronizedMap (new HashMap<String, Long> ());
        portInterBandwidth = new ConcurrentHashMap<String, Long> ();
        portNameMap = new ConcurrentHashMap<NodePortTuple, String> ();
        switchMap = new ConcurrentHashMap<Long, Boolean> ();
        switchPortMap = new ConcurrentHashMap<Long, List<Short>> ();
        portQueueMap = new ConcurrentHashMap<LDUpdate, Boolean> ();
        log.info("at init of Haqos");

    }


    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApi.addRestletRoutable(new HaqosWebRoutable());
        topology.addListener(this);
        log.info("at startUp of Haqos");
    }


    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return "linkdiscovery".equals(name);
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {

        return Command.CONTINUE;
    }

}
