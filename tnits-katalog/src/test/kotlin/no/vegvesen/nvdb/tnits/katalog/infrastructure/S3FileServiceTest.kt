package no.vegvesen.nvdb.tnits.katalog.infrastructure

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.MinioClient
import io.mockk.every
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties

class S3FileServiceTest : ShouldSpec({

    should("transform S3 path slashes to underscores in filename") {
        // Arrange
        val objectName = "0105-speedLimit/2025-01-15T10-30-00Z/snapshot.xml.gz"
        val expectedFileName = "0105-speedLimit_2025-01-15T10-30-00Z_snapshot.xml.gz"

        val minioClient = mockk<MinioClient>()
        val minioProperties = mockk<MinioProperties>()
        val mockResponse = mockk<GetObjectResponse>(relaxed = true)

        every { minioProperties.bucket } returns "test-bucket"
        every { minioClient.getObject(any<GetObjectArgs>()) } returns mockResponse

        val service = S3FileService(minioClient, minioProperties)

        // Act
        val result = service.downloadFile(objectName)

        // Assert
        result.fileName shouldBe expectedFileName
    }
})
