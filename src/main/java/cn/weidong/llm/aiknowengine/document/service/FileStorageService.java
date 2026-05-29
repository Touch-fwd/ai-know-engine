package cn.weidong.llm.aiknowengine.document.service;

import cn.weidong.llm.aiknowengine.document.config.MinioProperties;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public FileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 确保 MinIO bucket 存在。
     * <p>
     * 当前业务上传后的文件需要能够直接通过 URL 访问，因此 bucket 首次创建时会按需设置公共读策略。
     *
     * @param publicRead 是否设置公共读
     */
    private void createBucketIfNotExists(boolean publicRead) throws Exception {
        String bucketName = properties.getBucketName();
        log.debug("Checking MinIO bucket, bucketName={}, publicRead={}", bucketName, publicRead);
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            log.info("MinIO bucket does not exist, creating bucket, bucketName={}", bucketName);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            // 设置 bucket 策略为公共读
            if (publicRead) {
                log.info("Setting MinIO bucket public read policy, bucketName={}", bucketName);
                String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]}]}";
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(bucketName)
                                .config(policy)
                        .build()
                );
            }
        } else {
            log.debug("MinIO bucket already exists, bucketName={}", bucketName);
        }
    }

    /**
     * 上传 Multipart 文件到 MinIO。
     *
     * @param file 上传文件
     * @param objectName MinIO 对象名
     * @return 文件访问 URL
     */
    public String uploadFile(MultipartFile file, String objectName) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("Start uploading multipart file to MinIO, bucketName={}, objectName={}, originalFilename={}, size={}",
                properties.getBucketName(), objectName, file.getOriginalFilename(), file.getSize());
        createBucketIfNotExists(true);// 这里可根据你自己的情况改成false，如果改成false，需要在这个方法最后调一次getPresignedUrl
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(resolveContentType(file.getContentType()))
                .build());
        String fileUrl = String.format("%s/%s/%s", normalizedEndpoint(), properties.getBucketName(), objectName);
        log.info("Multipart file uploaded to MinIO, bucketName={}, objectName={}, elapsedMs={}, fileUrl={}",
                properties.getBucketName(), objectName, System.currentTimeMillis() - startTime, fileUrl);
        return fileUrl;

    }

    /**
     * 上传字节数组到 MinIO，适用于 MinerU 生成的 md、图片、zip 等内存数据。
     *
     * @param objectName MinIO 对象名
     * @param content 文件内容
     * @param contentType 文件 MIME 类型
     * @return 文件访问 URL
     */
    public String uploadFile(String objectName, byte[] content, String contentType) throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("Start uploading bytes to MinIO, bucketName={}, objectName={}, size={}, contentType={}",
                properties.getBucketName(), objectName, content == null ? 0 : content.length, contentType);
        createBucketIfNotExists(true);
        try (InputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectName)
                            .stream(stream, content.length, -1)
                            .contentType(resolveContentType(contentType))
                            .build()
            );

            String fileUrl = String.format("%s/%s/%s", normalizedEndpoint(), properties.getBucketName(), objectName);
            log.info("Bytes uploaded to MinIO, bucketName={}, objectName={}, elapsedMs={}, fileUrl={}",
                    properties.getBucketName(), objectName, System.currentTimeMillis() - startTime, fileUrl);
            return fileUrl;
        }
    }

    /**
     * 从 MinIO 下载文件，调用方负责关闭返回的 InputStream。
     *
     * @param objectName MinIO 对象名
     * @return 文件输入流
     */
    public InputStream downloadFile(String objectName) throws Exception {
        log.info("Start downloading file from MinIO, bucketName={}, objectName={}", properties.getBucketName(), objectName);
        GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getBucketName())
                        .object(objectName)
                        .build());
        log.info("MinIO file download stream opened, bucketName={}, objectName={}", properties.getBucketName(), objectName);
        return response;
    }

    /**
     * 删除 MinIO 中的文件。
     *
     * @param objectName MinIO 对象名
     */
    public void deleteFile(String objectName) throws Exception {
        log.info("Deleting file from MinIO, bucketName={}, objectName={}", properties.getBucketName(), objectName);
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectName)
                .build());
        log.info("File deleted from MinIO, bucketName={}, objectName={}", properties.getBucketName(), objectName);
    }

    /**
     * 生成临时下载链接，默认有效期 7 天。
     *
     * @param objectName MinIO 对象名
     * @return 临时签名 URL
     */
    public String getPresignedUrl(String objectName) throws Exception {
        log.info("Generating MinIO presigned URL, bucketName={}, objectName={}, expiryDays=7",
                properties.getBucketName(), objectName);
        String presignedUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(properties.getBucketName())
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build());
        log.info("MinIO presigned URL generated, bucketName={}, objectName={}", properties.getBucketName(), objectName);
        return presignedUrl;
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private String normalizedEndpoint() {
        String endpoint = properties.endpointOrUrl();
        if (endpoint.endsWith("/")) {
            return endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }
}
