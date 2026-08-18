package run.halo.skillshowcase.service;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.skillshowcase.dto.SkillCategoryVo;
import run.halo.skillshowcase.extension.SkillCategory;

@Service
@Slf4j
public class SkillCategoryService {

    private final ReactiveExtensionClient client;
    private final SkillService skillService;

    public SkillCategoryService(ReactiveExtensionClient client, @Lazy SkillService skillService) {
        this.client = client;
        this.skillService = skillService;
    }

    public Mono<SkillCategoryVo> create(SkillCategory category) {
        if (category.getMetadata().getName() == null) {
            category.getMetadata().setName(
                category.getSpec().getDisplayName().toLowerCase().replaceAll("[^a-z0-9\\-]", "-"));
        }
        if (category.getSpec().getSortOrder() == null) {
            category.getSpec().setSortOrder(0);
        }
        return client.create(category)
            .doOnSuccess(c -> skillService.invalidateCategoryCache())
            .map(this::toVo);
    }

    public Mono<SkillCategoryVo> update(String name, SkillCategory category) {
        return client.fetch(SkillCategory.class, name)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Category not found: " + name)))
            .flatMap(existing -> {
                category.getMetadata().setName(name);
                category.getMetadata().setVersion(existing.getMetadata().getVersion());
                return client.update(category);
            })
            .doOnSuccess(c -> skillService.invalidateCategoryCache())
            .map(this::toVo);
    }

    public Mono<Void> delete(String name) {
        return client.fetch(SkillCategory.class, name)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Category not found: " + name)))
            .flatMap(client::delete)
            .doOnSuccess(v -> skillService.invalidateCategoryCache())
            .then();
    }

    public Mono<SkillCategoryVo> getByName(String name) {
        return client.fetch(SkillCategory.class, name).map(this::toVo);
    }

    public Mono<java.util.List<SkillCategoryVo>> listAll() {
        return client.listAll(SkillCategory.class, new ListOptions(),
                Sort.by(Sort.Order.asc("metadata.name")))
            .map(this::toVo)
            .collectList()
            .map(list -> {
                list.sort((a, b) -> {
                    int cmp = Integer.compare(a.sortOrder() != null ? a.sortOrder() : 0,
                            b.sortOrder() != null ? b.sortOrder() : 0);
                    if (cmp != 0) return cmp;
                    return a.name().compareTo(b.name());
                });
                return list;
            });
    }

    private SkillCategoryVo toVo(SkillCategory c) {
        return new SkillCategoryVo(
            c.getMetadata().getName(),
            c.getSpec().getDisplayName(),
            c.getSpec().getDescription(),
            c.getSpec().getIcon(),
            c.getSpec().getColor(),
            c.getSpec().getSortOrder()
        );
    }
}