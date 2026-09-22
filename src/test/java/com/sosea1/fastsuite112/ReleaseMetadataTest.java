package com.sosea1.fastsuite112;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReleaseMetadataTest {

    @Test
    void bundlesTheRetroFastSuiteReleaseIdentityAndLogo() throws IOException {
        assertEquals("fastsuite", FastSuite112.MOD_ID);
        assertEquals("Retro FastSuite", FastSuite112.NAME);
        assertEquals("1.0.1", FastSuite112.VERSION);

        String metadata = readResource("/mcmod.info");
        assertTrue(metadata.contains("\"modid\": \"fastsuite\""));
        assertTrue(metadata.contains("\"name\": \"Retro FastSuite\""));
        assertTrue(metadata.contains("\"version\": \"1.0.1\""));
        assertTrue(metadata.contains("\"logoFile\": \"assets/fastsuite112/logo.png\""));
        try (InputStream logo = FastSuite112.class.getResourceAsStream("/assets/fastsuite112/logo.png")) {
            assertNotNull(logo, "Missing classpath resource: /assets/fastsuite112/logo.png");
        }
    }

    @Test
    void bundlesTheProjectMitLicenseWithBothCopyrightNotices() throws IOException {
        String license = readResource("/META-INF/LICENSE_fastsuite");

        assertTrue(license.contains("Copyright (c) 2021 Brennan Ward"));
        assertTrue(license.contains("Copyright (c) 2026 FastSuite112 contributors"));
    }

    private static String readResource(String path) throws IOException {
        InputStream stream = FastSuite112.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing classpath resource: " + path);
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
