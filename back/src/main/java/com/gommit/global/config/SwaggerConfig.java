package com.gommit.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";
    private static final String PAGEABLE_PARAM = "pageable";
    private static final String SORT_PARAM = "sort";

    private static final int EXAMPLE_PAGE_NUMBER = 0;
    private static final int EXAMPLE_PAGE_SIZE = 20;

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        return new OpenAPI()
                .info(new Info()
                        .title("꼬밋 API")
                        .description("소그룹(1~6인) 목표 챌린지 서비스 '꼬밋(Go!mmit)' REST API")
                        .version("v1.0.0"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerAuth))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }

    @Bean
    public OpenApiCustomizer swaggerPageableCustomizer() {
        return openApi -> openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(this::replacePageableParameters);
    }

    private void replacePageableParameters(Operation operation) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters == null || parameters.stream().noneMatch(this::isPageable)) {
            return;
        }
        parameters.removeIf(p -> isPageable(p) || SORT_PARAM.equals(p.getName()));
        parameters.addAll(pageableParameters());
    }

    private boolean isPageable(Parameter parameter) {
        return PAGEABLE_PARAM.equals(parameter.getName());
    }

    private List<Parameter> pageableParameters() {
        return List.of(
                queryParameter("page", "페이지 번호 (0부터 시작)").schema(new IntegerSchema().example(EXAMPLE_PAGE_NUMBER)),
                queryParameter("size", "한 번에 가져올 개수").schema(new IntegerSchema().example(EXAMPLE_PAGE_SIZE)));
    }

    private Parameter queryParameter(String name, String description) {
        return new Parameter().in("query").name(name).description(description);
    }
}
