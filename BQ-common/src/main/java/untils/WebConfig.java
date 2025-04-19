package untils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.storage.local-path}")
    private String uploadPath;

    @Value("${file.storage.access-path}")
    private String accessPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将物理路径映射为虚拟路径
        String formattedPath = uploadPath.replace("\\", "/");

        registry.addResourceHandler(accessPath)
                .addResourceLocations("file:" + formattedPath + "/");
    }
}