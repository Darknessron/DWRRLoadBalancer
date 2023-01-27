package ron.testclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestClientApplication {

  public static void main(String[] args)
      throws URISyntaxException, IOException, InterruptedException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode req = mapper.createObjectNode();
    req.put("Name", "Ron");
    req.put("Email", "cg12192001@gmail.com");
    HttpRequest request = HttpRequest.newBuilder(new URI("http://localhost:9090/dummy"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(req.toString())).build();

    HttpResponse<String> response = HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
    System.out.println(response.body());
  }

}
