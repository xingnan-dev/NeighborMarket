package com.neighbormarket.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Semaphore;

@Component
public class ImageNormalizer {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private final long maxBytes;
    private final int minDimension;
    private final int maxDimension;
    private final long maxPixels;
    private final Semaphore permits;

    public ImageNormalizer(@Value("${app.image.max-bytes:8388608}") long maxBytes,
                           @Value("${app.image.min-dimension:64}") int minDimension,
                           @Value("${app.image.max-dimension:6000}") int maxDimension,
                           @Value("${app.image.max-pixels:20000000}") long maxPixels,
                           @Value("${app.image.max-concurrent-decodes:2}") int maxConcurrentDecodes) {
        this.maxBytes = maxBytes;
        this.minDimension = minDimension;
        this.maxDimension = maxDimension;
        this.maxPixels = maxPixels;
        this.permits = new Semaphore(Math.max(1, maxConcurrentDecodes));
        ImageIO.setUseCache(false);
    }

    public NormalizedImage normalize(MultipartFile file) {
        if (file == null || file.isEmpty()) bad("请选择图片");
        if (file.getSize() > maxBytes) tooLarge();
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            return decode(file);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片处理被中断");
        } finally {
            if (acquired) permits.release();
        }
    }

    private NormalizedImage decode(MultipartFile file) {
        Path output = null;
        String expected = null;
        try (InputStream raw = file.getInputStream()) {
            byte[] signature = raw.readNBytes(8);
            expected = magicFormat(signature);
            if (expected == null) bad("仅支持JPEG或PNG图片");
        } catch (IOException ex) {
            bad("图片无法读取");
        }
        try (InputStream raw = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(raw)) {
            if (input == null) bad("图片无法读取");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) bad("图片内容无效");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String extension = format.equals("jpeg") || format.equals("jpg") ? "jpg"
                        : format.equals("png") ? "png" : null;
                if (extension == null || !extension.equals(expected)) bad("图片格式与内容不一致");
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                BufferedImage encoded = "jpg".equals(extension) ? flatten(decoded) : decoded;
                output = Files.createTempFile("neighbormarket-image-", "." + extension);
                if (!ImageIO.write(encoded, extension, output.toFile())) bad("图片无法重新编码");
                if (Files.size(output) > maxBytes) tooLarge();
                return new NormalizedImage(output, extension,
                        "jpg".equals(extension) ? "image/jpeg" : "image/png");
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ignored) { }
            if (ex instanceof ResponseStatusException response) throw response;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片内容无效");
        }
    }

    private String magicFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff)
            return "jpg";
        if (bytes.length >= PNG.length) {
            for (int i = 0; i < PNG.length; i++) if (bytes[i] != PNG[i]) return null;
            return "png";
        }
        return null;
    }

    private void validateDimensions(int width, int height) {
        if (width < minDimension || height < minDimension || width > maxDimension || height > maxDimension
                || (long) width * height > maxPixels) {
            bad("图片尺寸需在64至6000像素之间，且总像素不超过2000万");
        }
    }

    private BufferedImage flatten(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return target;
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static void tooLarge() {
        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过8MB");
    }

    public record NormalizedImage(Path path, String extension, String mediaType) implements AutoCloseable {
        @Override public void close() {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { }
        }
    }
}
