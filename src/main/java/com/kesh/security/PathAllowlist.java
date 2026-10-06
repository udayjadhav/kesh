package com.kesh.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.kesh.config.KeshProperties;

@Component
public class PathAllowlist {

    private final List<Path> workRoots;
    private final List<Path> personalRoots;

    public PathAllowlist(KeshProperties properties) {
        this.workRoots = expandAll(properties.getFilesystem().getAllowedPaths());
        List<String> personal = properties.getFilesystem().getPersonalPaths();
        this.personalRoots = personal == null || personal.isEmpty()
                ? List.of()
                : expandAll(personal);
    }

    public static PathAllowlist forPaths(List<String> allowedPaths) {
        KeshProperties properties = new KeshProperties();
        properties.getFilesystem().setAllowedPaths(allowedPaths);
        return new PathAllowlist(properties);
    }

    public Path resolveAllowed(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new SecurityException("Path is required");
        }
        Path requested = expand(rawPath).toAbsolutePath().normalize();
        Path realRequested = resolveExisting(requested);
        for (Path allowed : rootsForCurrentDesk()) {
            Path realAllowed = resolveExisting(allowed);
            if (isInside(realRequested, realAllowed)) {
                return realRequested;
            }
        }
        throw new SecurityException("Path is outside the configured allowlist: " + requested);
    }

    public List<Path> allowedRoots() {
        List<Path> copy = new ArrayList<>();
        for (Path root : rootsForCurrentDesk()) {
            copy.add(resolveExisting(root));
        }
        return List.copyOf(copy);
    }

    private List<Path> rootsForCurrentDesk() {
        com.kesh.workspace.WorkspaceMode mode = com.kesh.workspace.Workspace.current();
        if (mode == com.kesh.workspace.WorkspaceMode.PERSONAL) {
            return personalRoots.isEmpty() ? workRoots : personalRoots;
        }
        return workRoots;
    }

    private static List<Path> expandAll(List<String> paths) {
        if (paths == null) {
            return List.of();
        }
        return paths.stream()
                .map(PathAllowlist::expand)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toList();
    }

    static Path expand(String raw) {
        String value = raw.trim();
        if (value.startsWith("~/") || value.equals("~")) {
            String home = System.getProperty("user.home");
            if (value.equals("~")) {
                return Path.of(home);
            }
            return Path.of(home, value.substring(2));
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("$home/")
                || value.toLowerCase(Locale.ROOT).startsWith("${user.home}/")) {
            String home = System.getProperty("user.home");
            int slash = value.indexOf('/');
            return Path.of(home, value.substring(slash + 1));
        }
        return Path.of(value);
    }

    private static Path resolveExisting(Path path) {
        try {
            if (Files.exists(path)) {
                return path.toRealPath();
            }
            Path parent = path.getParent();
            Path missing = path.getFileName();
            List<Path> missingParts = new ArrayList<>();
            missingParts.add(missing);
            while (parent != null && !Files.exists(parent)) {
                missingParts.add(0, parent.getFileName());
                parent = parent.getParent();
            }
            Path resolvedParent = parent == null ? path.getRoot() : parent.toRealPath();
            Path resolved = resolvedParent;
            for (Path part : missingParts) {
                if (part != null) {
                    resolved = resolved.resolve(part);
                }
            }
            return resolved.normalize();
        } catch (IOException e) {
            throw new SecurityException("Unable to resolve path: " + path, e);
        }
    }

    private static boolean isInside(Path candidate, Path root) {
        Path c = candidate.normalize();
        Path r = root.normalize();
        return c.equals(r) || c.startsWith(r);
    }
}
