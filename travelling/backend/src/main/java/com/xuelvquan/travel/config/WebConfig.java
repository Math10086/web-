package com.xuelvquan.travel.config;

import com.xuelvquan.travel.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 托管前端页面+本地图片资源。
     *
     * 前端页面统一挂在/travelling路径下：
     * 只需要在IDEA里启动后端一个程序，浏览器打开http://localhost:8080/travelling/即可访问整个网站。
     * 若dist不存在（或打包成jar后），回退到classpath:/static/。
     *
     * 另外两类资源仍保留在根路径，不受前端挂载路径影响：
     *   /api/** —— 接口（前端axios里写的是绝对路径/api）
     *   /images/** —— 本地图片（数据库里存的就是/images/xxx.jpg这种绝对路径）
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/travelling/**")
                .addResourceLocations(resolveFrontendLocations())
                .setCachePeriod(0);
        // 本地图片：开发时优先用target/classes（上传接口写入的位置），找不到再回退源码目录/classpath
        registry.addResourceHandler("/images/**")
                .addResourceLocations(resolveImageLocations())
                .setCachePeriod(0);
    }

    /**
     * 探测本地图片目录的真实位置。
     * 开发时上传接口写入target/classes/static/images/，所以优先用该目录；
     * IDEA直接运行或命令行启动时工作目录不同，分别尝试backend/target与target；
     * 都找不到时回退classpath。
     */
    private String[] resolveImageLocations() {
        java.nio.file.Path cwd = Paths.get(System.getProperty("user.dir")).normalize();
        String[] candidates = {
                cwd.resolve("target/classes/static/images").normalize().toString(),
                cwd.resolve("../target/classes/static/images").normalize().toString(),
                cwd.resolve("src/main/resources/static/images").normalize().toString(),
                cwd.resolve("../src/main/resources/static/images").normalize().toString()
        };
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String c : candidates) {
            if (Paths.get(c).toFile().isDirectory()) {
                list.add("file:" + c + "/");
            }
        }
        list.add("classpath:/static/images/");
        return list.toArray(new String[0]);
    }

    /**
     * 探测frontend/dist的真实位置。
     * 工作目录取决于启动方式（IDEA运行配置、命令行mvn spring-boot:run各不相同），
     * 所以把几种常见情况都试一遍：backend目录（../frontend/dist）与项目根目录（frontend/dist），
     * 哪个下面真的有index.html就用哪个；都找不到时回退classpath:/static/。
     */
    private String[] resolveFrontendLocations() {
        java.nio.file.Path cwd = Paths.get(System.getProperty("user.dir")).normalize();
        String[] candidates = {
                cwd.resolve("../frontend/dist").normalize().toString(),
                cwd.resolve("frontend/dist").normalize().toString()
        };
        for (String c : candidates) {
            if (Paths.get(c, "index.html").toFile().exists()) {
                return new String[]{"file:" + c + "/", "classpath:/static/"};
            }
        }
        return new String[]{"classpath:/static/"};
    }
}
