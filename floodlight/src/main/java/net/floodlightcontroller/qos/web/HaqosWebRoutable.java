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

import org.restlet.Context;
import org.restlet.routing.Router;

import net.floodlightcontroller.linkdiscovery.web.DirectedLinksResource;
import net.floodlightcontroller.linkdiscovery.web.ExternalLinksResource;
import net.floodlightcontroller.linkdiscovery.web.LinksResource;
import net.floodlightcontroller.restserver.RestletRoutable;

public class HaqosWebRoutable implements RestletRoutable {

    /**
     * Set the base path for the Haqos
     */
    @Override
    public String basePath() {
        return "/wm/haqos";
    }

  /**
     * Create the Restlet router and bind to the proper resources.
     */
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/queues/{switchId}/list/json", HaqosResource.class); // GET
        router.attach("/addqueues/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/{bandwidth}/{tcp}/{src-ip}/json", HaqosResource.class); // PUT
        router.attach("/hasbandwidth/{src-dpid}/{dst-dpid}/{bandwidth}/json", HaqosBandwidthResource.class); // GET
        router.attach("/createEfQ/{src-dpid}/{dst-dpid}/{bandwidth}/{tp-src}/{tp-dst}/json", HaqosEfResource.class); // PUT
        router.attach("/reserveInterBw/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/{bandwidth}/{tp-src}/{src-ip}/json", HaqosInterResource.class); // PUT
        return router;
    }

}
