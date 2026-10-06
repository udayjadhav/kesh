package com.kesh.workspace;

public final class Workspace {

    private static final ThreadLocal<WorkspaceMode> CURRENT = new ThreadLocal<>();

    private Workspace() {
    }

    public static void set(WorkspaceMode mode) {
        CURRENT.set(mode);
    }

    public static WorkspaceMode current() {
        return CURRENT.get();
    }

    public static WorkspaceMode currentOr(WorkspaceMode fallback) {
        WorkspaceMode mode = CURRENT.get();
        return mode == null ? fallback : mode;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
