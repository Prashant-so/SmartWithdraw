package com.smartwithdraw.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure text logic (no Bukkit): finds options that exist in the jar's config.yml
 * but not in the server's, and inserts them as text, comments included.
 * Existing lines are never edited, so admin values, comments and layout stay.
 */
final class ConfigMerge {

    record Result(String text, List<String> added, boolean changed) {
    }

    private record Node(String path, int indent, int comment, int line, int end) {
    }

    private static final Pattern KEY = Pattern.compile("^( *)([A-Za-z0-9_-]+):(?:\\s.*)?$");
    private static final int MAX_STEPS = 500;

    private ConfigMerge() {
    }

    static Result merge(String serverText, String jarText, Predicate<String> existsInYaml,
                        Set<String> skip, String versionKey, int serverVersion, int targetVersion) {

        String eol = serverText.contains("\r\n") ? "\r\n" : "\n";
        List<String> server = split(serverText);
        List<String> jar = split(jarText);
        List<Node> jarNodes = scan(jar);

        List<String> added = new ArrayList<>();
        Set<String> skipped = new HashSet<>();

        for (int step = 0; step < MAX_STEPS; step++) {
            Set<String> have = new HashSet<>();
            for (Node n : scan(server)) have.add(n.path());

            Node missing = null;
            for (Node n : jarNodes) {
                String p = n.path();
                if (isSkipped(p, skip) || have.contains(p) || skipped.contains(p)
                        || existsInYaml.test(p)) {
                    continue;
                }
                missing = n;
                break;
            }
            if (missing == null) break;

            if (insert(server, jar, missing)) {
                added.add(missing.path());
            } else {
                skipped.add(missing.path());
            }
        }

        boolean stamped = serverVersion < targetVersion && stamp(server, versionKey, targetVersion);
        String text = String.join(eol, server) + eol;
        return new Result(text, added, !added.isEmpty() || stamped);
    }

    // ── inserting ────────────────────────────────────────────────────

    private static boolean insert(List<String> server, List<String> jar, Node j) {
        List<String> block = new ArrayList<>(jar.subList(j.comment(), j.end() + 1));
        int dot = j.path().lastIndexOf('.');

        if (dot < 0) {
            List<String> out = reindent(block, -j.indent());
            if (out == null) return false;
            if (!server.isEmpty() && !server.get(server.size() - 1).isBlank()) server.add("");
            server.addAll(out);
            return true;
        }

        String parentPath = j.path().substring(0, dot);
        List<Node> nodes = scan(server);
        Node parent = null;
        for (Node n : nodes) {
            if (n.path().equals(parentPath)) parent = n;
        }
        if (parent == null) return false;

        String parentLine = server.get(parent.line());
        String after = parentLine.substring(parentLine.indexOf(':') + 1).trim();
        if (!after.isEmpty() && !after.startsWith("#")) return false; // inline value, not a block

        int childIndent = parent.indent() + 2;
        for (Node n : nodes) {
            String p = n.path();
            if (p.startsWith(parentPath + ".") && p.indexOf('.', parentPath.length() + 1) < 0) {
                childIndent = n.indent();
                break;
            }
        }

        List<String> out = reindent(block, childIndent - j.indent());
        if (out == null) return false;
        server.addAll(parent.end() + 1, out);
        return true;
    }

    private static List<String> reindent(List<String> block, int shift) {
        List<String> out = new ArrayList<>();
        for (String l : block) {
            if (l.isBlank()) {
                out.add("");
            } else if (shift >= 0) {
                out.add(" ".repeat(shift) + l);
            } else {
                int lead = 0;
                while (lead < l.length() && l.charAt(lead) == ' ') lead++;
                if (lead < -shift) return null;
                out.add(l.substring(-shift));
            }
        }
        return out;
    }

    /** Only touches an existing top-level "config-version:" line. */
    private static boolean stamp(List<String> lines, String key, int value) {
        Pattern p = Pattern.compile("^" + Pattern.quote(key) + ":.*$");
        for (int i = 0; i < lines.size(); i++) {
            if (p.matcher(lines.get(i)).matches()) {
                String updated = key + ": " + value;
                if (updated.equals(lines.get(i))) return false;
                lines.set(i, updated);
                return true;
            }
        }
        return false;
    }

    // ── reading structure ────────────────────────────────────────────

    private static List<String> split(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r?\\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static boolean isSkipped(String path, Set<String> skip) {
        for (String s : skip) {
            if (path.equals(s) || path.startsWith(s + ".")) return true;
        }
        return false;
    }

    private static List<Node> scan(List<String> lines) {
        List<Integer> lineAt = new ArrayList<>();
        List<Integer> indentAt = new ArrayList<>();
        List<String> keyAt = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = KEY.matcher(lines.get(i));
            if (m.matches()) {
                lineAt.add(i);
                indentAt.add(m.group(1).length());
                keyAt.add(m.group(2));
            }
        }

        int n = lineAt.size();
        String[] paths = new String[n];
        Deque<Integer> stack = new ArrayDeque<>();
        for (int k = 0; k < n; k++) {
            while (!stack.isEmpty() && indentAt.get(stack.peek()) >= indentAt.get(k)) stack.pop();
            paths[k] = stack.isEmpty() ? keyAt.get(k) : paths[stack.peek()] + "." + keyAt.get(k);
            stack.push(k);
        }

        List<Node> nodes = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int boundary = lines.size();
            for (int j = k + 1; j < n; j++) {
                if (indentAt.get(j) <= indentAt.get(k)) {
                    boundary = lineAt.get(j);
                    break;
                }
            }
            int end = boundary - 1;
            while (end > lineAt.get(k) && isBlankOrComment(lines.get(end))) end--;

            int comment = lineAt.get(k);
            while (comment > 0 && lines.get(comment - 1).trim().startsWith("#")) comment--;

            nodes.add(new Node(paths[k], indentAt.get(k), comment, lineAt.get(k), end));
        }
        return nodes;
    }

    private static boolean isBlankOrComment(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith("#");
    }
                         }
