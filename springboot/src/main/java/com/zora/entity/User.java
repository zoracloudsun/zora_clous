package com.zora.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户实体")
public class User {
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "用户 ID（自增主键）", example = "1")
    private Integer id;

    @TableField(value = "email")
    @Schema(description = "邮箱（唯一，用于登录）", example = "user@example.com")
    private String email;

    @Schema(hidden = true)
    private String password;

    // ==================== 微信登录字段 ====================
    @Schema(description = "微信 openid（每个微信号对每个应用唯一）", example = "o6_bmjrPTlm6_2sgVt7hMZOPfL2M")
    private String openid;

    @Schema(description = "微信昵称", example = "张三")
    private String nickname;

    @Schema(description = "微信头像 URL", example = "https://thirdwx.qlogo.cn/mmopen/...")
    private String avatar;

    // ==================== RBAC 角色权限 ====================
    @Schema(description = "用户角色：user 普通用户, admin 管理员", example = "user", allowableValues = { "user", "admin" })
    private String role;

    public User() {
    }

    public User(Integer id, String email, String password) {
        this.id = id;
        this.email = email;
        this.password = password;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOpenid() {
        return openid;
    }

    public void setOpenid(String openid) {
        this.openid = openid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
