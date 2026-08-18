package com.canmakan.backend.user.repository;

/** Account fields required to construct a Spring Security user in one query. */
public interface AuthenticationAccountView {

    Long getUserId();

    String getEmail();

    String getPasswordHash();

    Boolean getActive();

    String getRoleName();
}
