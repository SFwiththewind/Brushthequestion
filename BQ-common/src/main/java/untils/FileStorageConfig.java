package untils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Value("${file.storage.local-path}")
    private String localPath;

    @Value("${file.storage.access-path}")
    private String accessPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将本地存储路径映射为网络可访问路径
        registry.addResourceHandler(accessPath)
                .addResourceLocations("file:" + localPath + "/");
    }
}