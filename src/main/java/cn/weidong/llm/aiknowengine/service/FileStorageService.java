package cn.weidong.llm.aiknowengine.service;

import cn.weidong.llm.aiknowengine.config.MinioProperties;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public FileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    // 确保 bucket 存在
    private void createBucketIfNotExists(boolean publicRead) throws Exception {
        String bucketName = properties.getBucketName();
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            // 设置 bucket 策略为公共读
            if (publicRead) {
                String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]}]}";
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(bucketName)
                                .config(policy)
                                .build()
                );
            }
        }
    }
    // 上传文件
    public String uploadFile(MultipartFile file, String objectName) throws Exception {
        createBucketIfNotExists(true);// 这里可根据你自己的情况改成false，如果改成false，需要在这个方法最后调一次getPresignedUrl
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(resolveContentType(file.getContentType()))
                .build());
        return String.format("%s/%s/%s", normalizedEndpoint(), properties.getBucketName(), objectName);

    }

    /**
     * 上传文件
     */
    public String uploadFile(String objectName, byte[] content, String contentType) throws Exception {
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

            return String.format("%s/%s/%s", normalizedEndpoint(), properties.getBucketName(), objectName);
        }
    }

    // 下载文件（返回 InputStream）
    public InputStream downloadFile(String objectName) throws Exception {
        GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getBucketName())
                        .object(objectName)
                        .build());
        return response;
    }

    // 删除文件
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectName)
                .build());
    }

    // 生成临时下载链接（带签名，有效期 7 天）
    public String getPresignedUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(properties.getBucketName())
                        .object(objectName)
                        .expiry(7, TimeUnit.DAYS)
                        .build());
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
