package dev.ayo.devinbridge.domain;

import org.jetbrains.annotations.Nullable;

public record StatusSnapshot(String sessionId, DevinStatus status, @Nullable String prUrl) {
}
