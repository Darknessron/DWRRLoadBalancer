# Project Goal
Implement the DWRR (Dynamic Weighted Round-Robin) LoadBalancer.  
The Loadbalancer will calculate the nodes loading and dispatch the request to lower loading node.  

## Work flow
1. Start DWRRLoadBalancer (default port is 9090)
2. Start multiple DummyServer instances with random port.  
   The DummyServer will call the "register" API from DWRRLoadBalancer to register itself.
3. If a request comes to DWRRLoadBalancer, it will follow the register order to dispatch the request.
4. After the request completed, DWRRLoadBalancer will change the node's weight according its response time.
5. If following node is busy (low weight), then the incoming request will pass to the next node. 

## Nodes Health Check
1. DWRRLoadBalancer will check the node status every 30 seconds.
2. If the node still **CAN** connect but its status is not **UP**, then will move the node to unavailable list.
3. DWRRLoadBalancer will check those unavailable nodes if they are back to work every 300 seconds.

## Technical stack
- [spring-boot-starter-web](https://spring.io/guides/gs/spring-boot/#initial)
- [spring boot actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [lombok](https://projectlombok.org/)
