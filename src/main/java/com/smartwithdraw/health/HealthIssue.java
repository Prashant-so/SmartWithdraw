package com.smartwithdraw.health;

import java.util.List;

public record HealthIssue(
        Severity severity,
        String id,
        String title,
        String detail,
        String fix,
        List<Link> links
) {

    public enum Severity { INFO, WARNING, CRITICAL }

    public record Link(String label, String url) {
    }
}
