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

package net.floodlightcontroller.qos.web;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.core.web.ControllerSwitchesResource;
import net.floodlightcontroller.qos.IQoSService;
import net.floodlightcontroller.routing.Link;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamLinkResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StreamLinkResource.class);
    
    @Get("json")
    public Map<DatapathId, Set<Link>>  getLinks() {
        IQoSService qosService =
                (IQoSService)getContext().getAttributes().
                    get(IQoSService.class.getCanonicalName());
        
        return qosService.getLinks();
    }
}
