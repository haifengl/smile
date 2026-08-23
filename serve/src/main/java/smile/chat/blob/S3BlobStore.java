/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import smile.chat.BlobStorageConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3-compatible {@link BlobStore} using AWS SDK v2.
 *
 * @author Haifeng Li
 */
public final class S3BlobStore implements BlobStore, AutoCloseable {
    private static final Logger logger = Logger.getLogger(S3BlobStore.class);

    private final S3Client client;
    private final String bucket;

    /**
     * @param config blob storage configuration with S3 settings.
     */
    public S3BlobStore(BlobStorageConfig config) {
        String bucketName = config.s3Bucket()
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "smile.blob.s3.bucket is required when smile.blob.storage=s3"));
        String region = config.s3Region()
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "smile.blob.s3.region is required when smile.blob.storage=s3"));

        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        Optional<String> accessKey = config.s3AccessKey().filter(s -> !s.isBlank());
        Optional<String> secretKey = config.s3SecretKey().filter(s -> !s.isBlank());
        if (accessKey.isPresent() && secretKey.isPresent()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.get(), secretKey.get())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        config.s3Endpoint().filter(s -> !s.isBlank()).ifPresent(endpoint -> {
            builder.endpointOverride(URI.create(endpoint));
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        });

        this.client = builder.build();
        this.bucket = bucketName;
        logger.infof("S3 blob store bucket=%s region=%s endpoint=%s",
                bucket, region, config.s3Endpoint().orElse("default"));
    }

    @Override
    public void put(String key, byte[] data, String contentType) throws IOException {
        try {
            PutObjectRequest.Builder req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);
            if (contentType != null && !contentType.isBlank()) {
                req.contentType(contentType);
            }
            client.putObject(req.build(), RequestBody.fromBytes(data));
        } catch (RuntimeException e) {
            throw new IOException("S3 put failed for key=" + key, e);
        }
    }

    @Override
    public void put(String key, InputStream data, long length, String contentType) throws IOException {
        try {
            PutObjectRequest.Builder req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);
            if (contentType != null && !contentType.isBlank()) {
                req.contentType(contentType);
            }
            RequestBody body = length >= 0
                    ? RequestBody.fromInputStream(data, length)
                    : RequestBody.fromBytes(data.readAllBytes());
            client.putObject(req.build(), body);
        } catch (RuntimeException e) {
            throw new IOException("S3 put failed for key=" + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) throws IOException {
        try {
            return Optional.of(client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            throw new IOException("S3 get failed for key=" + key, e);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (RuntimeException e) {
            throw new IOException("S3 delete failed for key=" + key, e);
        }
    }

    @Override
    public void deletePrefix(String prefix) throws IOException {
        try {
            String continuation = null;
            do {
                var listReq = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .continuationToken(continuation)
                        .build();
                var list = client.listObjectsV2(listReq);
                List<S3Object> objects = list.contents();
                if (objects != null && !objects.isEmpty()) {
                    List<ObjectIdentifier> ids = new ArrayList<>(objects.size());
                    for (S3Object obj : objects) {
                        ids.add(ObjectIdentifier.builder().key(obj.key()).build());
                    }
                    client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(Delete.builder().objects(ids).build())
                            .build());
                }
                continuation = Boolean.TRUE.equals(list.isTruncated())
                        ? list.nextContinuationToken() : null;
            } while (continuation != null);
        } catch (RuntimeException e) {
            throw new IOException("S3 deletePrefix failed for prefix=" + prefix, e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
