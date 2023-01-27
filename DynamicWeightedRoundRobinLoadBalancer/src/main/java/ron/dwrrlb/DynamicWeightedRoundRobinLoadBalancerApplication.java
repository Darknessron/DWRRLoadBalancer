package ron.dwrrlb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DynamicWeightedRoundRobinLoadBalancerApplication {

  public static void main(String[] args) {
    SpringApplication.run(DynamicWeightedRoundRobinLoadBalancerApplication.class, args);
  }

}
