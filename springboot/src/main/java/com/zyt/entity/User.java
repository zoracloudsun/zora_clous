package com.zyt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

public class User {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField(value = "email")
    private String email;
    private String password;

    // ==================== 微信登录字段 ====================
    /** 微信 openid（每个微信号对每个应用唯一） */
    private String openid;
    /** 微信昵称 */
    private String nickname;
    /** 微信头像 URL */
    private String avatar;

    // ==================== RBAC 角色权限 ====================
    /** 用户角色：user 普通用户, admin 管理员 */
    private String role;

    public User() {}

    public User(Integer id, String email, String password) {
        this.id = id;
        this.email = email;
        this.password = password;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
