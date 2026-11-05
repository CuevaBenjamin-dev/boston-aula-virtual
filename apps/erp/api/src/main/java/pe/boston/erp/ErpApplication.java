package pe.boston.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Boston ERP")
@SpringBootApplication
public class ErpApplication {
  public static void main(String[] args) {
    SpringApplication.run(ErpApplication.class, args);
  }
}
