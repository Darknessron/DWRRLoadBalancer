package ron.dwrrlb.vo;

import lombok.Data;

@Data
public class ServerNode {

  private String serverName;
  private String address;
  private String uri;
  private String status;
  private double weight;
}
