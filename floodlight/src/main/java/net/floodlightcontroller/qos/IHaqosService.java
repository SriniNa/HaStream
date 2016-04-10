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

import java.util.Collection;
import java.util.List;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.util.MACAddress;
import org.openflow.protocol.statistics.OFStatistics;

public interface IHaqosService extends IFloodlightService {

    public void printTest();
    public void createQueuesOnPath(long srcId, String srcPort,
        long dstId, String dstPort, long bandwidth,
        short tcpPort, String srcIp);

    public boolean hasBandwidthOnPath(long srcId, long dstId, long bandwidth);
    public boolean createEfQueue(long srcId, long dstId,
        long bandwidth, short tpSrc, short tpDst);
    public boolean reserveInterBandwidth(long srcId, String srcPort, long dstId,
                    String dstPort, long bandwidth, short tpSrc, String srcIp);

    public List <OFStatistics> getQueuesOnSwitch(long switchId);
}
