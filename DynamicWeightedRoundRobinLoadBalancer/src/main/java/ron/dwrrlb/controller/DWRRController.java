package ron.dwrrlb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ron.dwrrlb.vo.ServerNode;

@Controller
@Slf4j
@RequestMapping("/*")
public class DWRRController {

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final HttpClient client = HttpClient.newBuilder().build();

  private final int HEAVY_LOADING_BAR = 3000;
  private final int LIGHT_LOADING_BAR = 2000;
  private final int DEFAULT_WEIGHT = 100;

  private final AtomicLong count = new AtomicLong();
  private final List<ServerNode> availableServers = new ArrayList<>();
  private final List<ServerNode> unavailableServers = new ArrayList<>();

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> loadBalance(@RequestBody JsonNode jsonNode)
      throws URISyntaxException, IOException, InterruptedException {
    if (count.get() == Long.MAX_VALUE) {
      count.set(0);
    }
    int serverSize = availableServers.size();
    if (serverSize == 0) {
      log.info("No node available");
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    log.debug("{} nodes available", serverSize);

    int index = (int) (count.get() % serverSize);
    ServerNode node = availableServers.get(index);

    JsonNode result;
    // Loading too heavy, skip to less loading node.
    if (node.getWeight() < DEFAULT_WEIGHT / 2 && serverSize != 1) {
      log.debug("node {} loading too heavy, skip to next node", node.getServerName());

      //Check other nodes' weight
      int ind = IntStream.range(0, availableServers.size())
          .filter(i -> availableServers.get(i).getWeight() >= DEFAULT_WEIGHT / 2).findFirst().orElse(-1);

      if (ind < 0) {
        //All nodes are busy, follow original order.
        result = processRequest(node, jsonNode);
      } else {
        //Jump to less loading node
        result = processRequest(availableServers.get(index), jsonNode);
      }
    }
    //Loading is normal, process the request
    result = processRequest(node, jsonNode);
    return ResponseEntity.ok(result);
  }

  private JsonNode processRequest(ServerNode node, JsonNode jsonNode)
      throws IOException, URISyntaxException, InterruptedException {

    HttpRequest req = HttpRequest.newBuilder().uri(new URI(node.getAddress() + "/" + node.getUri()))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(jsonNode.toString())).build();
    long startMillisecond = System.currentTimeMillis();
    String response = client.send(req, BodyHandlers.ofString()).body();
    long endMillisecond = System.currentTimeMillis();
    JsonNode resp = new ObjectMapper().readTree(response);

    long processTime = endMillisecond - startMillisecond;
    log.info("Node:{}  Process Time: {} ms, weight: {}", node.getServerName(), processTime,
        node.getWeight());
    // Process speed less than 2 seconds, resume the weight to 100
    if (processTime < LIGHT_LOADING_BAR) {
      node.setWeight(DEFAULT_WEIGHT);
    } else if (processTime > HEAVY_LOADING_BAR) {
      // Process speed more than 3 seconds, decrease the weight
      node.setWeight(node.getWeight() / 2);
    }
    count.getAndIncrement();
    return resp;
  }

  @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Boolean> register(@RequestBody @NonNull ServerNode node) {
    if (!isValidNode(node)) {
      return ResponseEntity.badRequest().build();
    }
    lock.writeLock().lock();
    try {
      log.info("Add new node name: {} , address: {}", node.getServerName(), node.getAddress());
      node.setWeight(DEFAULT_WEIGHT);
      availableServers.add(node);
    } finally {
      lock.writeLock().unlock();
    }
    return ResponseEntity.ok(true);
  }

  @GetMapping(value = "/servers", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> checkServersStatus()  {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode result = mapper.createArrayNode();
    ObjectNode node;
    ServerNode server;
    lock.readLock().lock();
    try {
      for (int i = 0; i< availableServers.size(); i++)  {
        node = mapper.createObjectNode();
        server = availableServers.get(i);
        node.put("Name", server.getServerName());
        node.put("Address", server.getAddress());
        node.put("Uri", server.getUri());
        node.put("Weight", server.getWeight());

        result.add(node);
      }
    }finally {
      lock.readLock().unlock();
    }
    return ResponseEntity.ok(result);
  }

  private String getHealthStatus(ServerNode node)
      throws URISyntaxException, IOException, InterruptedException {
    String healthStatus = null;

    ObjectMapper mapper = new ObjectMapper();
    JsonNode healthResult = null;

    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(node.getAddress() + "/actuator/health")).GET().build();
    HttpResponse<String> response;
    try {
      response = client.send(request, BodyHandlers.ofString());
      healthResult = mapper.readTree(response.body());
    } catch (ConnectException ce) {
      //Can't connect to actuator
      return null;
    }
    healthStatus = healthResult.get("status").asText();

    return healthStatus;
  }

  @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
  private void healthCheckJob() throws URISyntaxException, IOException, InterruptedException {
    healthCheck(availableServers, false);
  }

  @Scheduled(fixedRate = 300, timeUnit = TimeUnit.SECONDS)
  private void checkUnavailableServersJob()
      throws URISyntaxException, IOException, InterruptedException {
    healthCheck(unavailableServers, true);
  }

  private void healthCheck(List<ServerNode> list, boolean isUnavailable)
      throws URISyntaxException, IOException, InterruptedException {
    if (list.isEmpty()) {
      return;
    }
    Iterator<ServerNode> it = list.iterator();
    ServerNode node;
    String healthStatus;
    while (it.hasNext()) {
      node = it.next();
      healthStatus = getHealthStatus(node);
      if (healthStatus == null) {
        //Can't connect to actuator
        log.info("node {} can't connect", node.getServerName());
        lock.writeLock().lock();
        try {
          it.remove();
        } finally {
          lock.writeLock().unlock();
        }
        continue;
      }
      switch (healthStatus) {
        case "DOWN", "OUT_OF_SERVICE", "UNKNOWN" -> {
          //Still can connect to actuator
          log.info("Node {} is temporary unreachable", node.getServerName());
          //Node is temporary unreachable, move to unavailableServers
          lock.writeLock().lock();
          try {
            if (!isUnavailable) {
              unavailableServers.add(node);
              it.remove();
            }
          } finally {
            lock.writeLock().unlock();
          }
        }
        case "UP" -> {
          if (isUnavailable) {
            log.info("Node {} resume to work", node.getServerName());
            availableServers.add(node);
            it.remove();
          }
        }
      }
    }
  }


  private boolean isValidNode(@NonNull ServerNode node) {
    if (!StringUtils.hasText(node.getServerName())) {
      return false;
    }
    if (!StringUtils.hasText(node.getAddress())) {
      return false;
    }
    if (!StringUtils.hasText(node.getUri())) {
      return false;
    }
    return true;
  }
}
