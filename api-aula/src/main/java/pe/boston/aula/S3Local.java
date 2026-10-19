package pe.boston.aula;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.model.*;

@Component public class S3Local implements AlmacenamientoObjetos {
  private static final String BUCKET="aula-privada";
  private final S3Client interno;
  private final S3Presigner publico;
  public S3Local(@Value("${BOSTON_S3_ENDPOINT}") String endpoint,@Value("${BOSTON_S3_PUBLIC_ENDPOINT}") String publicoEndpoint,
      @Value("${BOSTON_S3_ACCESS_KEY}") String access,@Value("${BOSTON_S3_SECRET_KEY}") String secret) {
    var credenciales=StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret));
    this.interno=S3Client.builder().region(Region.US_EAST_1).endpointOverride(URI.create(endpoint)).credentialsProvider(credenciales).forcePathStyle(true).build();
    this.publico=S3Presigner.builder().region(Region.US_EAST_1).endpointOverride(URI.create(publicoEndpoint)).credentialsProvider(credenciales).serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    try { interno.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build()); }
    catch(Exception e) { interno.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build()); }
  }
  public String iniciar(String clave,String tipo) { return interno.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(BUCKET).key(clave).contentType(tipo).build()).uploadId(); }
  public URI autorizarParte(String clave,String uploadId,int numero) {
    var solicitud=UploadPartRequest.builder().bucket(BUCKET).key(clave).uploadId(uploadId).partNumber(numero).build();
    return URI.create(publico.presignUploadPart(UploadPartPresignRequest.builder().signatureDuration(Duration.ofMinutes(20)).uploadPartRequest(solicitud).build()).url().toString());
  }
  public void completar(String clave,String uploadId,List<Map<String,Object>> partes) {
    var lista=partes.stream().map(p -> CompletedPart.builder().partNumber(((Number)p.get("numero")).intValue()).eTag((String)p.get("etag")).build()).toList();
    interno.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(BUCKET).key(clave).uploadId(uploadId).multipartUpload(CompletedMultipartUpload.builder().parts(lista).build()).build());
  }
  public long tamano(String clave) { return interno.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(clave).build()).contentLength(); }
  public byte[] cabecera(String clave) { return interno.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(clave).range("bytes=0-15").build()).asByteArray(); }
  public byte[] contenido(String clave) { return interno.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(clave).build()).asByteArray(); }
  public void abortar(String clave,String uploadId) { interno.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(BUCKET).key(clave).uploadId(uploadId).build()); }
  public URI leer(String clave,Duration vigencia) { return URI.create(publico.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(vigencia).getObjectRequest(GetObjectRequest.builder().bucket(BUCKET).key(clave).build()).build()).url().toString()); }
  public URI descargar(String clave,String nombre,Duration vigencia) {
    String seguro=nombre.replaceAll("[^a-zA-Z0-9._-]","_");
    return URI.create(publico.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(vigencia)
      .getObjectRequest(GetObjectRequest.builder().bucket(BUCKET).key(clave).responseContentDisposition("attachment; filename=\""+seguro+"\"").build()).build()).url().toString());
  }
  public void guardarFixture(String clave,byte[] contenido,String tipo) {
    interno.putObject(PutObjectRequest.builder().bucket(BUCKET).key(clave).contentType(tipo).build(),RequestBody.fromBytes(contenido));
  }
  public void eliminar(String clave) { interno.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(clave).build()); }
  public boolean disponible() { try { interno.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build()); return true; } catch(Exception e) { return false; } }
}
