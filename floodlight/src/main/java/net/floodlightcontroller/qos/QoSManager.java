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

package net.floodlightcontroller.qos;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.HAListenerTypeMarker;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IHAListener;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.qos.web.QoSWebRoutable;
import net.floodlightcontroller.util.ActionUtils;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.InstructionUtils;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.routing.Link;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowRemovedReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.ver10.OFFlowRemovedReasonSerializerVer10;
import org.projectfloodlight.openflow.protocol.ver11.OFFlowRemovedReasonSerializerVer11;
import org.projectfloodlight.openflow.protocol.ver12.OFFlowRemovedReasonSerializerVer12;
import org.projectfloodlight.openflow.protocol.ver13.OFFlowRemovedReasonSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFFlowRemovedReasonSerializerVer14;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This module is responsible for maintaining a set of static flows on
 * switches. This is just a big 'ol dumb list of flows and something external
 * is responsible for ensuring they make sense for the network.
 */
public class QoSManager
implements IFloodlightModule, IQoSService, IOFMessageListener {
	protected static Logger log = LoggerFactory.getLogger(QoSManager.class);
	public static final String qosName = "qos";

	public static final int QOS_APP_ID = 30;
	static {
		AppCookie.registerApp(QOS_APP_ID, qosName);
	}


	protected IFloodlightProviderService floodlightProviderService;
	protected ITopologyService topology;
	protected IRestApiService restApiService;





	public IFloodlightProviderService getFloodlightProvider() {
		return floodlightProviderService;
	}

	public void setFloodlightProvider(IFloodlightProviderService floodlightProviderService) {
		this.floodlightProviderService = floodlightProviderService;
	}




	@Override
	public Map<DatapathId, Set<Link>> getLinks() {
		return topology.getAllLinks();
	}


	@Override
	public String getName() {
		return qosName;
	}


	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		return Command.CONTINUE;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;  // no dependency for non-packet in
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;  // no dependency for non-packet in
	}

	// IFloodlightModule

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IQoSService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>,
		IFloodlightService> m =
		new HashMap<Class<? extends IFloodlightService>,
		IFloodlightService>();
		m.put(IQoSService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(ITopologyService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		topology = context.getServiceImpl(ITopologyService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
	} 

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		restApiService.addRestletRoutable(new QoSWebRoutable());
	}



}
