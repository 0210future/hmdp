package com.hmdp.user.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    @Resource
    private IUserService userService;

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long id) {
        return Result.ok(userService.getById(id));
    }
}