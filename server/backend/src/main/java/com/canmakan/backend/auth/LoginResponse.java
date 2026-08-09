package com.canmakan.backend.auth;

import java.util.List;

/**
 * Session fields for the web client after password login.
 * No JWT yet — clients store {@code userId} and send {@code X-User-Id}.
 * 
 * @author Amelia
 * @author YangMaowei
 */
public record LoginResponse(
    Long userId,
    String displayName,
    List<String> roles,
    boolean prototype
) {
}
