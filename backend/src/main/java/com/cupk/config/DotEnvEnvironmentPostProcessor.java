package com.cupk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动加载项目根目录的 .env 文件到 Spring 环境（本地开发用）。
 *
 * Spring Boot 默认不读 .env；本处理器在配置加载前把 .env 中的 KEY=VALUE
 * 注入环境（优先级最低，不会覆盖真实的系统环境变量）。
 *
 * 查找顺序：当前工作目录的 .env，其次是父目录（backend 目录下运行时 ../.env 即项目根）。
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String FILE_NAME = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 幂等：防止被多种注册机制重复加载时重复添加
        if (environment.getPropertySources().contains("dotenv")) {
            return;
        }
        Path file = locateEnvFile();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }

        Map<String, Object> props = new LinkedHashMap<>();
        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // 兼容 "export KEY=VALUE" 前缀
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // 去掉首尾引号
                if (value.length() >= 2) {
                    char first = value.charAt(0);
                    char last = value.charAt(value.length() - 1);
                    if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                        value = value.substring(1, value.length() - 1);
                    }
                }
                props.put(key, value);
            }
            if (!props.isEmpty()) {
                // addLast：优先级最低，绝不覆盖命令行/系统环境变量中的同名值
                environment.getPropertySources().addLast(new MapPropertySource("dotenv", props));
            }
        } catch (IOException e) {
            // 读取失败不阻塞启动
        }
    }

    private Path locateEnvFile() {
        Path cwd = Paths.get(FILE_NAME);
        if (Files.isRegularFile(cwd)) {
            return cwd;
        }
        Path parent = Paths.get("..", FILE_NAME);
        if (Files.isRegularFile(parent)) {
            return parent;
        }
        return null;
    }
}
