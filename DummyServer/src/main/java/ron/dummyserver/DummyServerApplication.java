package ron.dummyserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class DummyServerApplication {

  @Autowired
  private ServletWebServerApplicationContext webServerAppCtx;

  @Value("${loadbalancer.url}")
  private String registerURL;

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(DummyServerApplication.class);
    app.run(args);
  }

  @Bean
  public CommandLineRunner register() {
    return (args) -> {

      int port = webServerAppCtx.getWebServer().getPort();

      ObjectNode node = new ObjectMapper().createObjectNode();
      node.put("serverName", "DummyServer-" + port);
      node.put("address", "http://localhost:" + port);
      node.put("uri", "dummy");

      log.info("Start register to DWRRLoadBalancer, URL: {}", registerURL);
      HttpRequest request = HttpRequest.newBuilder().uri(new URI(registerURL))
          .POST(BodyPublishers.ofString(node.toString()))
          .header("Content-Type", "application/json").build();
      HttpResponse<String> response = HttpClient.newHttpClient()
          .send(request, BodyHandlers.ofString());
      System.out.println(response.body());
      log.info("Register completed");
    };
  }

}
