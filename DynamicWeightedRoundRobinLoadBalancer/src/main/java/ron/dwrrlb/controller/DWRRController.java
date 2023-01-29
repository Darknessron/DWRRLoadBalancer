package ron.dwrrlb.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
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

  private volatile long count = 0;
  private final List<ServerNode> availableServers = new ArrayList<>();
  private final List<ServerNode> unavailableServers = new ArrayList<>();

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> loadBalance(@RequestBody JsonNode jsonNode)
      throws URISyntaxException, IOException, InterruptedException {
    if (count == Long.MAX_VALUE) count = 0;
    ServerNode node;
    lock.readLock().lock();
    int serverSize = availableServers.size();
    log.info("{} nodes available", serverSize);
    try {
      int index = (int) (count % serverSize);
      node = availableServers.get(index);
    }finally {
      lock.readLock().unlock();
    }

    JsonNode result;
    // Loading too heavy, skip to less loading node.
    if (node.getWeight() < 50 && serverSize != 1)  {
      log.info("node {} loading too heavy, skip to next node", node.getServerName());

      //Check other nodes' weight
      int index = IntStream.range(0, availableServers.size()).filter(i -> availableServers.get(i).getWeight() >= 50).findFirst().orElse(-1);

      if (index >= 0 ) {
        //All nodes are busy, follow original order.
        result = processRequest(node, jsonNode);
      }else {
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

    HttpRequest req = HttpRequest.newBuilder().uri(new URI(node.getAddress() +"/"+ node.getUri()))
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(jsonNode.toString())).build();
    long startMillisecond = System.currentTimeMillis();
    String response = client.send(req, BodyHandlers.ofString()).body();
    long endMillisecond = System.currentTimeMillis();
    JsonNode resp = new ObjectMapper().readTree(response);

    long processTime = endMillisecond - startMillisecond;
    log.info("Node:{}  Process Time: {} ms, weight: {}", node.getServerName(), processTime, node.getWeight());
    // Process speed less than 2 seconds, resume the weight to 100
    if (processTime < 2000) {
      node.setWeight(100);
    } else if (processTime > 3000) {
      // Process speed more than 3 seconds, decrease the weight
      node.setWeight(node.getWeight() / 2);
    }
    lock.writeLock().lock();
    try {
      count++;
    }finally {
      lock.writeLock().unlock();
    }
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
      node.setWeight(100);
      availableServers.add(node);
    } finally {
      lock.writeLock().unlock();
    }
    return ResponseEntity.ok(true);
  }

  @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
  private void checkHealth() throws URISyntaxException, IOException, InterruptedException {
    if (availableServers.isEmpty()) {
      return;
    }
    Iterator<ServerNode> it = availableServers.iterator();
    ServerNode node;
    ObjectMapper mapper = new ObjectMapper();

    while (it.hasNext()) {
      node = it.next();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new URI(node.getAddress() + "/actuator/health")).GET().build();
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      JsonNode result = mapper.readTree(response.body());

      if (result.get("status").asText().equals("UP")) {
        log.info("Server {} is health", node.getServerName());
        continue;
      }

      if (result.get("status").asText().equals("DOWN")) {
        lock.writeLock().lock();
        try {
          it.remove();
        }finally {
          lock.writeLock().unlock();
        }
      } else {
        lock.writeLock().lock();
        try {
          unavailableServers.add(node);
          it.remove();
        }finally {
          lock.writeLock().unlock();
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
    return StringUtils.hasText(node.getStatus()) || node.getStatus().equals("UP");
  }
}
