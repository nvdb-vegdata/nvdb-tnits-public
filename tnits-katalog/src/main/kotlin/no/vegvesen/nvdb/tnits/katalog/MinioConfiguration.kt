package no.vegvesen.nvdb.tnits.katalog

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfiguration {

    @Bean
    fun minioClient(minioProperties: MinioProperties): MinioClient {
        return MinioClient.builder().endpoint(minioProperties.endpoint).credentials(minioProperties.accessKey, minioProperties.secretKey).build()
    }
}
