
BIG SWITCH FLOODLIGHT SDN CONTROLLER'S NEW QoS MODULE


This is the new Haqos module added in the Floodlight SDN controller.

The module is added as per:
R.Wallner and R.Cannistra, "An SDN approach: Quality of Service using Big Switch's Floodlight Open-source controller,"
APAN, vol. 35, pp. 14-19, 2013.

Unlike the paper mentioned above, we don't use Diffserv using DSCP based approach.
Also, the QoS is implemented between application components and replicas 
requiring bandwidth guarnatees and for latency sensitive applications.

The REST APIs added:

1) 
/wm/haqos/queues/{switchId}/list/json

The above API is used to retrieve all the QoS queues on the switch
specified by switchId.


2)
/wm/haqos/addqueues/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/{bandwidth}/{tcp}/{src-ip}/json

The above API (invoked using PUT) is used to reserve bandwidth between two 
application components. This uses 'Aggregation of RSVP' method to reserve 
bandwidth. The bandwidth is reserved between application component at source 
switch: src-dpid and port src-port and the second component located at switch:
dst-dpid and port dst-port. 'bandwidth' is specified in bps. 'tcp' specified 
the port used by component 1 to communicate with component 2. src-ip is the ip
used by the component1.


3)
/wm/haqos/hasbandwidth/{src-dpid}/{dst-dpid}/{bandwidth}/json

The above API is used to check whether there exists enough bandwidth between
source switch: src-dpid and destination switch: dst-dpid. 'bandwidth' is 
specified in bps. This API uses the routing and topology modules to get the
route between source and destination. It then checks whethere enough
bandwidth is available on the route.


4)
/wm/haqos/reserveInterBw/{src-dpid}/{src-port}/{dst-dpid}/{dst-port}/{bandwidth}/{tp-src}/{src-ip}/json

The above API (invoked using PUT) is similar to 2) but the method used to 
reserve is 'Aggregation of RSVP using DSCP'.





NOTE: To run 'Aggregation of RSVP using DSCP' one has to change the value of
field 'useAggrRsvpWithDscp' in Haqos.java to true and compile the SDN code.

And to run 'Aggregation of RSVP' and 'Diffserv using DSCP' the above field
value should be set to false.
