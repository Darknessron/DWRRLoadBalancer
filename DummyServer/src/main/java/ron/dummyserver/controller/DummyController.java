package ron.dummyserver.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@Slf4j
@RequestMapping("/dummy")
public class DummyController {

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<JsonNode> dummy(@RequestBody JsonNode request) throws InterruptedException {
    //Simulate process
    Random random = new Random();
    int sleepMilliseconds = random.ints(100, 5000).findFirst().getAsInt();
    log.debug("DummyController sleep for {} milliseconds", sleepMilliseconds);
    Thread.sleep(sleepMilliseconds);
    //End of Simulate process
    return ResponseEntity.ok(request);
  }
}
