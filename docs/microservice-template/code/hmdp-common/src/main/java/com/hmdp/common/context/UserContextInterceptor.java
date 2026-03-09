package com.hmdp.common.context;

import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        if (!StringUtils.hasText(userId)) {
            return true;
        }
        RequestUser user = new RequestUser();
        user.setId(Long.valueOf(userId));
        user.setNickName(request.getHeader("X-User-NickName"));
        user.setIcon(request.getHeader("X-User-Icon"));
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}