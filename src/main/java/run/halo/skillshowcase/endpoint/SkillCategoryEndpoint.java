package run.halo.skillshowcase.endpoint;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.skillshowcase.dto.SkillCategoryVo;
import run.halo.skillshowcase.extension.SkillCategory;
import run.halo.skillshowcase.service.SkillCategoryService;

@Component
@RequiredArgsConstructor
public class SkillCategoryEndpoint implements RouterFunction<ServerResponse> {

    private static final String PREFIX = "/apis/console.api.skill-showcase.halo.run/v1alpha1";

    private final SkillCategoryService service;

    private final RouterFunction<ServerResponse> delegate = RouterFunctions.route()
        .GET(PREFIX + "/categories", this::list)
        .POST(PREFIX + "/categories", this::create)
        .GET(PREFIX + "/categories/{name}", this::get)
        .PUT(PREFIX + "/categories/{name}", this::update)
        .DELETE(PREFIX + "/categories/{name}", this::delete)
        .build();

    @Override
    public Mono<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        return delegate.route(request);
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(service.listAll(), SkillCategoryVo.class);
    }

    private Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(SkillCategory.class)
            .flatMap(service::create)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        String name = request.pathVariable("name");
        return service.getByName(name)
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> update(ServerRequest request) {
        String name = request.pathVariable("name");
        return request.bodyToMono(SkillCategory.class)
            .flatMap(cat -> service.update(name, cat))
            .flatMap(vo -> ServerResponse.ok().bodyValue(vo))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> delete(ServerRequest request) {
        String name = request.pathVariable("name");
        return service.delete(name)
            .then(ServerResponse.ok().build())
            .onErrorResume(e -> ServerResponse.notFound().build());
    }
}