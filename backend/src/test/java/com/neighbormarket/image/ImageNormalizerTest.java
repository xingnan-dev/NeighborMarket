package com.neighbormarket.image;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageNormalizerTest {
    private final ImageNormalizer normalizer = new ImageNormalizer(8 * 1024 * 1024, 64, 6000, 20_000_000, 2);

    @Test
    void validPngIsDecodedAndReencoded() throws Exception {
        byte[] source = image("png", BufferedImage.TYPE_INT_ARGB);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "text/plain", source);

        try (ImageNormalizer.NormalizedImage normalized = normalizer.normalize(file)) {
            assertEquals("png", normalized.extension());
            assertEquals("image/png", normalized.mediaType());
            assertTrue(Files.size(normalized.path()) > 0);
            BufferedImage decoded = ImageIO.read(normalized.path().toFile());
            assertEquals(96, decoded.getWidth());
        }
    }

    @Test
    void rejectsSvgEvenWhenClientClaimsPng() {
        MockMultipartFile file = new MockMultipartFile("file", "attack.png", "image/png",
                "<svg onload='alert(1)'></svg>".getBytes());
        assertThrows(ResponseStatusException.class, () -> normalizer.normalize(file));
    }

    @Test
    void rejectsImageBelowMinimumDimensions() throws Exception {
        BufferedImage tiny = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(tiny, "jpg", output);
        MockMultipartFile file = new MockMultipartFile("file", "tiny.jpg", "image/jpeg", output.toByteArray());
        assertThrows(ResponseStatusException.class, () -> normalizer.normalize(file));
    }

    @Test
    void oversizedUploadReturnsPayloadTooLarge() {
        ImageNormalizer smallLimit = new ImageNormalizer(2, 64, 6000, 20_000_000, 1);
        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> smallLimit.normalize(file));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    private byte[] image(String format, int type) throws Exception {
        BufferedImage image = new BufferedImage(96, 80, type);
        var graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
