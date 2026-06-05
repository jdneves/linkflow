package br.com.linkflow.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Component
public class StorageClient {

    private S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public StorageClient(
        @Value("${linkflow.storage.endpoint:}")   String endpoint,
        @Value("${linkflow.storage.access-key:}") String accessKey,
        @Value("${linkflow.storage.secret-key:}") String secretKey,
        @Value("${linkflow.storage.bucket:linkflow-media}") String bucket,
        @Value("${linkflow.storage.public-url:}") String publicBaseUrl
    ) {
        this.bucket        = bucket;
        this.publicBaseUrl = publicBaseUrl;
        if (!endpoint.isBlank()) {
            this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of("auto"))
                .build();
        } else {
            log.warn("R2_ENDPOINT não configurado — uploads desabilitados.");
        }
    }

    /**
     * Faz upload de um arquivo de áudio MP3.
     * Retorna a URL pública — necessária para o HeyGen baixar o arquivo.
     */
    public String uploadAudio(byte[] bytes, UUID jobId) {
        String chave = "audios/" + jobId + ".mp3";
        return upload(bytes, chave, "audio/mpeg");
    }

    /**
     * Faz upload de um vídeo MP4.
     * Retorna a URL pública para exibir ao usuário.
     */
    public String uploadVideo(byte[] bytes, UUID jobId) {
        String chave = "videos/" + jobId + ".mp4";
        return upload(bytes, chave, "video/mp4");
    }

    private String upload(byte[] bytes, String chave, String contentType) {
        if (s3 == null) throw new IllegalStateException("Storage não configurado (R2_ENDPOINT ausente).");
        try {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(chave)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build(),
                RequestBody.fromBytes(bytes)
            );

            String url = publicBaseUrl + "/" + chave;
            log.info("Upload concluído: {} ({} bytes)", url, bytes.length);
            return url;

        } catch (Exception e) {
            log.error("Falha no upload do arquivo {}: {}", chave, e.getMessage(), e);
            throw new RuntimeException("Falha ao fazer upload do arquivo.");
        }
    }
}
