package no.vegvesen.nvdb.tnits.katalog.infrastructure

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.minio.GetObjectResponse
import io.minio.MinioClient
import io.minio.StatObjectResponse
import io.mockk.every
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties

class S3FileServiceTest : ShouldSpec({

    fun setupMocks(fileSize: Long = 1000L): Triple<MinioClient, MinioProperties, S3FileService> {
        val minioClient = mockk<MinioClient>()
        val minioProperties = mockk<MinioProperties>()
        val mockResponse = mockk<GetObjectResponse>(relaxed = true)
        val mockObjectStat = mockk<StatObjectResponse>()

        every { minioProperties.bucket } returns "test-bucket"
        every { mockObjectStat.size() } returns fileSize
        every { minioClient.statObject(any()) } returns mockObjectStat
        every { minioClient.getObject(any()) } returns mockResponse

        val service = S3FileService(minioClient, minioProperties)
        return Triple(minioClient, minioProperties, service)
    }

    should("transform S3 path slashes to underscores in filename") {
        val objectName = "0105-SpeedLimit/2025-01-15T10-30-00Z/snapshot.xml.gz"
        val expectedFileName = "0105-SpeedLimit_2025-01-15T10-30-00Z_snapshot.xml.gz"

        val (_, _, service) = setupMocks()

        val result = service.downloadFile(objectName)

        result.fileName shouldBe expectedFileName
    }

    should("retrieve file size from MinIO metadata") {
        val objectName = "0105-SpeedLimit/2025-01-15T10-30-00Z/snapshot.xml.gz"
        val expectedSize = 5242880L // 5 MB

        val (_, _, service) = setupMocks(expectedSize)

        val result = service.downloadFile(objectName)

        result.size shouldBe expectedSize
    }

    should("determine content type based on file extension") {
        val objectName = "data/file.xml.gz"
        val (_, _, service) = setupMocks()

        val result = service.downloadFile(objectName)

        result.contentType shouldBe "application/gzip"
    }
})
