package com.thinkfirst.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @jakarta.validation.constraints.Email(message = "请输入正确的邮箱地址")
        @jakarta.validation.constraints.Size(max=190,message="邮箱过长")
        @NotBlank(message = "邮箱不能为空") String email,
        @jakarta.validation.constraints.Size(max=72,message="密码过长")
        @NotBlank(message = "密码不能为空") String password
) {
    @Override public String toString() { return "敏感请求（内容已隐藏）"; }
}
