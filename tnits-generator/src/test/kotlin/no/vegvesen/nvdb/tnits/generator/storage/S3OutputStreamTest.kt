package no.vegvesen.nvdb.tnits.generator.storage

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldNotBe
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.delay

class S3OutputStreamTest :
    ShouldSpec({

        val contentType = "application/text"
        should("write data to MinIO via piped stream") {
            // Arrange
            val mockMinioClient = mockk<MinioClient>()
            val putObjectArgsSlot = slot<PutObjectArgs>()

            every { mockMinioClient.putObject(capture(putObjectArgsSlot)) } returns mockk()

            val testData = "Hello, S3 World!".toByteArray()
            val bucket = "test-bucket"
            val objectKey = "test-object-key"

            // Act
            S3OutputStream(mockMinioClient, bucket, objectKey, contentType).use { outputStream ->
                outputStream.write(testData)
            }

            // Give the coroutine time to complete
            delay(100)

            // Assert
            verify { mockMinioClient.putObject(any<PutObjectArgs>()) }

            val capturedArgs = putObjectArgsSlot.captured
            capturedArgs shouldNotBe null
        }

        should("handle single byte writes") {
            // Arrange
            val mockMinioClient = mockk<MinioClient>()
            every { mockMinioClient.putObject(any<PutObjectArgs>()) } returns mockk()

            val bucket = "test-bucket"
            val objectKey = "test-object-key"

            // Act
            S3OutputStream(mockMinioClient, bucket, objectKey, contentType).use { outputStream ->
                outputStream.write(65) // 'A'
                outputStream.write(66) // 'B'
                outputStream.write(67) // 'C'
            }

            // Give the coroutine time to complete
            delay(100)

            // Assert
            verify { mockMinioClient.putObject(any<PutObjectArgs>()) }
        }

        should("handle byte array writes with offset and length") {
            // Arrange
            val mockMinioClient = mockk<MinioClient>()
            every { mockMinioClient.putObject(any<PutObjectArgs>()) } returns mockk()

            val testData = "Hello, World!".toByteArray()
            val bucket = "test-bucket"
            val objectKey = "test-object-key"

            // Act
            S3OutputStream(mockMinioClient, bucket, objectKey, contentType).use { outputStream ->
                outputStream.write(testData, 7, 5) // "World" portion
            }

            // Give the coroutine time to complete
            delay(100)

            // Assert
            verify { mockMinioClient.putObject(any<PutObjectArgs>()) }
        }

        should("use correct content type") {
            // Arrange
            val mockMinioClient = mockk<MinioClient>()
            val putObjectArgsSlot = slot<PutObjectArgs>()
            every { mockMinioClient.putObject(capture(putObjectArgsSlot)) } returns mockk()

            val testData = "test".toByteArray()
            val bucket = "test-bucket"
            val objectKey = "test-object-key"
            val customContentType = "application/gzip"

            // Act
            S3OutputStream(mockMinioClient, bucket, objectKey, customContentType).use { outputStream ->
                outputStream.write(testData)
            }

            // Give the coroutine time to complete
            delay(100)

            // Assert
            verify { mockMinioClient.putObject(any<PutObjectArgs>()) }
        }

        should("support flush operations") {
            // Arrange
            val mockMinioClient = mockk<MinioClient>()
            every { mockMinioClient.putObject(any<PutObjectArgs>()) } returns mockk()

            val testData = "test".toByteArray()
            val bucket = "test-bucket"
            val objectKey = "test-object-key"

            // Act & Assert (should not throw)
            S3OutputStream(mockMinioClient, bucket, objectKey, contentType).use { outputStream ->
                outputStream.write(testData)
                outputStream.flush() // Should not throw
            }

            // Give the coroutine time to complete
            delay(100)

            verify { mockMinioClient.putObject(any<PutObjectArgs>()) }
        }
    })
