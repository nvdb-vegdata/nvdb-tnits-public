package no.vegvesen.nvdb.tnits.katalog.infrastructure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.common.MinioTestHelper
import no.vegvesen.nvdb.tnits.common.extensions.clear
import no.vegvesen.nvdb.tnits.common.extensions.listObjectNames
import no.vegvesen.nvdb.tnits.common.extensions.put
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties
import no.vegvesen.nvdb.tnits.katalog.core.exceptions.ClientException
import no.vegvesen.nvdb.tnits.katalog.core.exceptions.NotFoundException
import org.testcontainers.containers.MinIOContainer

class S3FileServiceIntegrationTest : ShouldSpec() {
    private val minioContainer: MinIOContainer = MinioTestHelper.createMinioContainer()
    private lateinit var minioClient: MinioClient
    private val testBucket = "test-bucket"
    private lateinit var service: S3FileService

    init {
        beforeSpec {
            minioContainer.start()
            minioClient = MinioTestHelper.createMinioClient(minioContainer)
            MinioTestHelper.waitForMinioReady(minioClient)
            MinioTestHelper.ensureBucketExists(minioClient, testBucket)

            val minioProperties = MinioProperties(bucket = testBucket)
            service = S3FileService(minioClient, minioProperties)
        }

        afterSpec {
            minioContainer.stop()
        }

        beforeEach {
            clearBucket()
        }

        context("list") {
            should("return empty list when path has no objects") {
                val result = service.list("nonexistent/", recursive = false)
                result.shouldBeEmpty()
            }

            should("list immediate children only when recursive=false") {
                uploadTestFile("exports/speedlimit/2025-01-01/snapshot.xml.gz", "content1")
                uploadTestFile("exports/speedlimit/2025-01-02/snapshot.xml.gz", "content2")
                uploadTestFile("exports/roadnet/2025-01-01/snapshot.xml.gz", "content3")

                val result = service.list("exports/", recursive = false)

                result shouldHaveSize 2
                result.shouldContainExactlyInAnyOrder(
                    "exports/speedlimit/",
                    "exports/roadnet/",
                )
            }

            should("list all nested objects when recursive=true") {
                uploadTestFile("exports/speedlimit/2025-01-01/snapshot.xml.gz", "content1")
                uploadTestFile("exports/speedlimit/2025-01-02/snapshot.xml.gz", "content2")
                uploadTestFile("exports/roadnet/2025-01-01/snapshot.xml.gz", "content3")

                val result = service.list("exports/", recursive = true)

                result shouldHaveSize 3
                result.shouldContainExactlyInAnyOrder(
                    "exports/speedlimit/2025-01-01/snapshot.xml.gz",
                    "exports/speedlimit/2025-01-02/snapshot.xml.gz",
                    "exports/roadnet/2025-01-01/snapshot.xml.gz",
                )
            }

            should("deduplicate directory prefixes in non-recursive mode") {
                uploadTestFile("data/level1/file1.txt", "content1")
                uploadTestFile("data/level1/file2.txt", "content2")
                uploadTestFile("data/level2/file3.txt", "content3")

                val result = service.list("data/", recursive = false)

                result shouldHaveSize 2
                result.shouldContainExactlyInAnyOrder(
                    "data/level1/",
                    "data/level2/",
                )
            }

            should("handle root listing with empty path") {
                uploadTestFile("file1.txt", "content1")
                uploadTestFile("dir1/file2.txt", "content2")

                val result = service.list("", recursive = false)

                result shouldHaveSize 2
                result.shouldContain("file1.txt")
                result.shouldContain("dir1/")
            }

            should("handle paths with and without trailing slashes consistently") {
                uploadTestFile("exports/speedlimit/file.xml", "content")

                val resultWithSlash = service.list("exports/", recursive = true)
                val resultWithoutSlash = service.list("exports", recursive = true)

                resultWithSlash shouldBe resultWithoutSlash
                resultWithSlash.shouldContain("exports/speedlimit/file.xml")
            }

            should("list both files and subdirectories in non-recursive mode") {
                uploadTestFile("data/file1.txt", "content1")
                uploadTestFile("data/subdir/file2.txt", "content2")

                val result = service.list("data/", recursive = false)

                result shouldHaveSize 2
                result.shouldContainExactlyInAnyOrder(
                    "data/file1.txt",
                    "data/subdir/",
                )
            }
        }

        context("delete") {
            should("delete single file when exact match exists") {
                uploadTestFile("test-file.txt", "content")

                val result = service.delete("test-file.txt", recursive = false)

                result shouldHaveSize 1
                result.shouldContain("test-file.txt")

                val remaining = listAllObjects()
                remaining.shouldBeEmpty()
            }

            should("throw NOT_FOUND when non-recursive delete targets prefix that doesn't exist as object") {
                uploadTestFile("exports/file1.xml", "content1")
                uploadTestFile("exports/file2.xml", "content2")

                val exception = shouldThrow<NotFoundException> {
                    service.delete("exports/", recursive = false)
                }

                exception.message shouldContain "Object not found"
            }

            should("delete all objects under prefix when recursive=true") {
                uploadTestFile("exports/speedlimit/2025-01-01/snapshot.xml.gz", "content1")
                uploadTestFile("exports/speedlimit/2025-01-02/snapshot.xml.gz", "content2")
                uploadTestFile("exports/roadnet/2025-01-01/snapshot.xml.gz", "content3")
                uploadTestFile("other/file.txt", "content4")

                val result = service.delete("exports/", recursive = true)

                result shouldHaveSize 3
                result.shouldContainExactlyInAnyOrder(
                    "exports/speedlimit/2025-01-01/snapshot.xml.gz",
                    "exports/speedlimit/2025-01-02/snapshot.xml.gz",
                    "exports/roadnet/2025-01-01/snapshot.xml.gz",
                )

                val remaining = listAllObjects()
                remaining shouldHaveSize 1
                remaining.shouldContain("other/file.txt")
            }

            should("throw NOT_FOUND when object doesn't exist") {
                val exception = shouldThrow<NotFoundException> {
                    service.delete("nonexistent.txt", recursive = false)
                }

                exception.message shouldContain "Object not found"
            }

            should("throw NOT_FOUND when no objects found with prefix for recursive delete") {
                val exception = shouldThrow<NotFoundException> {
                    service.delete("nonexistent/", recursive = true)
                }

                exception.message shouldContain "No objects found"
            }

            should("handle recursive deletion of nested structure") {
                uploadTestFile("deep/level1/level2/level3/file.txt", "content")
                uploadTestFile("deep/level1/level2/file2.txt", "content2")
                uploadTestFile("deep/level1/file3.txt", "content3")

                val result = service.delete("deep/", recursive = true)

                result shouldHaveSize 3
                result.shouldContainExactlyInAnyOrder(
                    "deep/level1/level2/level3/file.txt",
                    "deep/level1/level2/file2.txt",
                    "deep/level1/file3.txt",
                )

                val remaining = listAllObjects()
                remaining.shouldBeEmpty()
            }

            should("throw NOT_FOUND when non-recursive delete targets prefix even if only one object matches") {
                uploadTestFile("exports/speedlimit/file.xml", "content")

                val exception = shouldThrow<NotFoundException> {
                    service.delete("exports/speedlimit/", recursive = false)
                }

                exception.message shouldContain "Object not found"

                // Verify nothing was deleted
                val remaining = listAllObjects()
                remaining shouldHaveSize 1
                remaining.shouldContain("exports/speedlimit/file.xml")
            }
        }
    }

    private fun uploadTestFile(objectKey: String, content: String) {
        minioClient.put(testBucket, objectKey, content.toByteArray())
    }

    private fun clearBucket() {
        minioClient.clear(testBucket)
    }

    private fun listAllObjects(): List<String> = minioClient.listObjectNames(testBucket, "", recursive = true)
}
