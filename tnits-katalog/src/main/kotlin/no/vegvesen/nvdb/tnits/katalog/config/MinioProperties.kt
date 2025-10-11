package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minio")
class MinioProperties(
    var endpoint: String = "",
    var accessKey: String = "",
    var secretKey: String = "",
    var bucket: String = "",
)
