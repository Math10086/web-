package com.xuelvquan.travel.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端history路由回退（SPA fallback）。
 *
 * 前端页面统一挂在/travelling路径下（Vue Router的base也是/travelling/）。
 * 直接刷新/travelling/circles/3这类子页面时后端没有对应资源会404，
 * 这里统一转发到/travelling/index.html，交给前端路由处理。
 *
 * 匹配规则：覆盖/travelling下1~3段路径，且「最后一段不带扩展名」才回退。
 * /travelling/assets/xxx.js这类静态资源（末段带点）不会被拦截，由WebConfig的资源处理器直接返回。
 *
 * 接口/api/** 和图片/images/** 都还在根路径（优先级更高的控制器/资源处理器负责），与本类无关。
 */
@Controller
public class SpaForwardController {

    /** 访问根路径时跳到/travelling/，保证只有一个入口 */
    @GetMapping("/")
    public String root() {
        return "redirect:/travelling/";
    }

    @GetMapping({
            "/travelling",
            "/travelling/",
            "/travelling/{p1:[^.]*}",
            "/travelling/{p1:[^.]*}/{p2:[^.]*}",
            "/travelling/{p1:[^.]*}/{p2:[^.]*}/{p3:[^.]*}"
    })
    public String forward() {
        return "forward:/travelling/index.html";
    }
}
