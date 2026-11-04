package com.hatodo.auth;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String displayName) {}
