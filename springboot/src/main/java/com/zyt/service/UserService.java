package com.zyt.service;

import com.zyt.utils.ResponseUtil;

public interface UserService {
    ResponseUtil register(String username, String password);
    ResponseUtil login(String username, String password);
    ResponseUtil logout(String token);
    ResponseUtil refresh(String refreshToken);
}
