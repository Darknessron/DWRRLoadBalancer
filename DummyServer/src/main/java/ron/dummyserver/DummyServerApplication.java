package ron.dummyserver;

import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class DummyServerApplication {

  @Value("${loadbalancer.url}")
  private String registerURL;

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(DummyServerApplication.class);
    app.setDefaultProperties(Collections.singletonMap("server.port", "8083"));
    app.run(args);
  }
  @Bean
  public CommandLineRunner register() {
    return (args) -> {
      log.info("Start register to DWRRLoadBalancer, URL: {}", registerURL);
      log.info("Register completed");
    };
  }

}
