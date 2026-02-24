package com.ssafy.S14P21A205.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 용도: OpenAPI(Swagger) 메타 정보 설정. */
@Configuration
public class OpenApiConfig {

    /** 용도: OpenAPI 문서 기본 정보 생성. */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("S14P21A205 API")
                        .version("v1")
                        .description("Swagger 상단의 Google 로그인 버튼으로 인증 후 Try it out을 사용할 수 있습니다."));
    }
}
