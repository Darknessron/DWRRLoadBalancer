package ron.dwrrlb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

  private final long count = 0;
  private final List<ServerNode> availableServers = new ArrayList<>();
  private final List<ServerNode> unavailableServers = new ArrayList<>();

  @PostMapping
  public ResponseEntity<JsonNode> loadBalance(@RequestBody JsonNode jsonNode) {
    return ResponseEntity.ok().build();
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
        it.remove();
      } else {
        unavailableServers.add(node);
        it.remove();
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
