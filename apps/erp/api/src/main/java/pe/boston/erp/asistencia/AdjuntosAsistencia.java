package pe.boston.erp.asistencia;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Comprobantes privados en el almacenamiento ya compartido; ERP guarda sus metadatos. */
@Component
public class AdjuntosAsistencia {
  private static final String BUCKET="erp-asistencia-privada";
  private final S3Client cliente;
  private volatile boolean preparado;

  public AdjuntosAsistencia(@Value("${BOSTON_S3_ENDPOINT:http://localhost:8333}") String endpoint,
      @Value("${BOSTON_S3_ACCESS_KEY:local-aula}") String access,
      @Value("${BOSTON_S3_SECRET_KEY:cambiar-local-s3}") String secret) {
    cliente=S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)))
      .forcePathStyle(true).build();
  }
  private synchronized void preparar() {
    if(preparado) return;
    try { cliente.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build()); }
    catch(Exception sinBucket) { cliente.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build()); }
    preparado=true;
  }
  public void guardar(String clave,String tipo,byte[] datos) {
    preparar();
    cliente.putObject(PutObjectRequest.builder().bucket(BUCKET).key(clave).contentType(tipo).build(),
      RequestBody.fromBytes(datos));
  }
  public byte[] leer(String clave) {
    return cliente.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(clave).build()).asByteArray();
  }
  public void eliminar(String clave) {
    cliente.deleteObject(b->b.bucket(BUCKET).key(clave));
  }
}
