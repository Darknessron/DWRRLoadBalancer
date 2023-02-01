package ron.dummyserver.Indicator;

import java.util.Random;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SimulateHealthIndicator implements HealthIndicator {

  private final Random random = new Random();

  @Override
  public Health getHealth(boolean includeDetails) {
    return health();
  }

  @Override
  public Health health() {
    int i = random.nextInt(10);
    Health health = switch (i) {
      case 0, 1, 2, 3, 4, 5, 6 -> Health.up().build();
      case 7 -> Health.down().build();
      case 8 -> Health.outOfService().build();
      case 9 -> Health.unknown().build();
      default -> throw new IllegalStateException("Unexpected value: " + i);
    };
    return health;
  }
}
