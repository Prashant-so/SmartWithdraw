package com.smartwithdraw.update;

public record ReleaseInfo(
        String version,
        String pageUrl,
        String downloadUrl,
        String assetName,
        long size,
        String sha256
) {
}
