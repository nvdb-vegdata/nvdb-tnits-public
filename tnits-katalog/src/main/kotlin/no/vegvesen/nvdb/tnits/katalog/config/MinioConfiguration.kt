package no.vegvesen.nvdb.tnits.katalog.config

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.common.infrastructure.MinioGateway
import no.vegvesen.nvdb.tnits.common.infrastructure.S3KeyValueStore
import no.vegvesen.nvdb.tnits.common.model.S3Config
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfiguration {

    @Bean
    fun minioClient(minioProperties: MinioProperties): MinioClient =
        MinioClient.builder().endpoint(minioProperties.endpoint).credentials(minioProperties.accessKey, minioProperties.secretKey).build()

    @Bean
    fun adminFlags(minioClient: MinioClient, minioProperties: MinioProperties) = S3KeyValueStore(
        MinioGateway(
            minioClient,
            S3Config(
                endpoint = minioProperties.endpoint,
                accessKey = minioProperties.accessKey,
                secretKey = minioProperties.secretKey,
                bucket = minioProperties.bucket,
            ),
        ),
    )
}
