package com.company.hrm.common.storage;

import com.company.hrm.common.exception.*;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path root;
    private final long maxSize;
    public LocalFileStorage(@Value("${hrm.documents.directory}") String directory,
            @Value("${spring.servlet.multipart.max-file-size}") String maxSize) {
        if (directory.isBlank()) throw new IllegalArgumentException("Thiếu thư mục tài liệu");
        this.root = Path.of(directory).toAbsolutePath().normalize();
        this.maxSize = org.springframework.util.unit.DataSize.parse(maxSize).toBytes();
        if (this.maxSize <= 0 || this.maxSize > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Giới hạn tài liệu phải trong khoảng 1 byte đến 10 MiB");
    }
    private Path resolve(String name) throws IOException {
        if (name == null || !name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new BusinessException(ErrorCode.HRM_VALIDATION_ERROR, "Tên lưu không hợp lệ");
        // Reject symlinks throughout the configured directory, not just the final file.
        Path current = root.getRoot();
        for (Path part : root) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Unsafe storage directory");
        }
        return root.resolve(name);
    }
    @Override public String store(byte[] content) {
        if (content.length == 0 || content.length > maxSize)
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID_FILE, "Dung lượng tài liệu không hợp lệ");
        String name = UUID.randomUUID().toString();
        boolean created = false;
        try {
            Path path = resolve(name);
            Files.createDirectories(root);
            try (var channel = Files.newByteChannel(path, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                var buffer = java.nio.ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
            return name;
        } catch (IOException e) {
            if (created) try { Files.deleteIfExists(root.resolve(name)); } catch (IOException ignored) { }
            throw failure();
        }
    }
    @Override public byte[] read(String name) {
        try (var channel = Files.newByteChannel(resolve(name), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             var input = Channels.newInputStream(channel)) {
            byte[] bytes = input.readNBytes((int) maxSize + 1);
            if (bytes.length > maxSize) throw failure();
            return bytes;
        } catch (NoSuchFileException e) {
            throw new BusinessException(ErrorCode.HRM_RESOURCE_NOT_FOUND, "Không tìm thấy tài liệu");
        } catch (IOException e) { throw failure(); }
    }
    @Override public void delete(String name) {
        try { Files.deleteIfExists(resolve(name)); }
        catch (IOException e) { throw failure(); }
    }
    private BusinessException failure() {
        return new BusinessException(ErrorCode.HRM_INTERNAL_ERROR, "Không thể truy cập kho tài liệu");
    }
}
