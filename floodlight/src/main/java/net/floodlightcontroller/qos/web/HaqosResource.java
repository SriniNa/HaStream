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

package net.floodlightcontroller.haqos.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.packet.IPv4;

import org.openflow.util.HexString;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaqosResource extends HaqosResourceBase {
    protected static Logger log = LoggerFactory.getLogger(HaqosResource.class);
    
    
    @Get("json")
    public Map<String, Object> retrieve() {
        HashMap<String, Object> result = new HashMap<String, Object> ();
        Object values = null;
        String switchId = (String) getRequestAttributes().get("switchId");

        values = getQueuesOnSwitch (switchId);
        result.put(switchId, values);
        return result;
    }


    @Put
    public String createQueuesOnPath() {
        long srcId =
            HexString.toLong((String) getRequestAttributes().get("src-dpid"));
        String srcPort = (String) getRequestAttributes().get("src-port");
        long dstId =
            HexString.toLong((String) getRequestAttributes().get("dst-dpid"));
        String dstPort = (String) getRequestAttributes().get("dst-port");
        long bandwidth =
            Long.parseLong((String) getRequestAttributes().get("bandwidth"));
        short tcpPort =
            Short.parseShort((String) getRequestAttributes().get("tcp"));
        String srcIp = (String) getRequestAttributes().get("src-ip");

        boolean result =
            createQueuesOnPath (srcId, srcPort, dstId, dstPort, bandwidth, tcpPort, srcIp);

        return "{\"status\":\"ok\"}";
    }


}
